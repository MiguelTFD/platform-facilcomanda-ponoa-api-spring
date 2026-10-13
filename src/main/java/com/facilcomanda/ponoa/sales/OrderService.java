package com.facilcomanda.ponoa.sales;

import com.facilcomanda.ponoa.catalog.CatalogPriceProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.inventory.StockService;
import com.facilcomanda.ponoa.table.RestaurantTableProvider;
import com.facilcomanda.ponoa.table.RestaurantTableView;
import com.facilcomanda.ponoa.table.TableState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService {

  private final OrderRepository orderRepository;
  private final CatalogPriceProvider catalogPriceProvider;
  private final StockService stockService;
  private final RestaurantTableProvider restaurantTableProvider;
  private final ApplicationEventPublisher eventPublisher;

  OrderService(
      OrderRepository orderRepository,
      CatalogPriceProvider catalogPriceProvider,
      StockService stockService,
      RestaurantTableProvider restaurantTableProvider,
      ApplicationEventPublisher eventPublisher) {
    this.orderRepository = orderRepository;
    this.catalogPriceProvider = catalogPriceProvider;
    this.stockService = stockService;
    this.restaurantTableProvider = restaurantTableProvider;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Long placeOrder(
      Long branchId,
      UUID userId,
      String idempotencyKey,
      String userEmail,
      PlaceOrderRequest request) {

    String resolvedTableName = null;
    if (request.tableId() != null) {
      RestaurantTableView table =
          restaurantTableProvider
              .findTableByIdAndBranch(request.tableId(), branchId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Table " + request.tableId() + " not found in branch " + branchId));

      if (TableState.OUT_OF_SERVICE.equals(table.state())) {
        throw new IllegalStateException(
            "Table " + request.tableId() + " is currently OUT_OF_SERVICE");
      }

      resolvedTableName =
          table.floorName() != null
              ? table.tableName() + " (" + table.floorName() + ")"
              : table.tableName();

      restaurantTableProvider.updateTableState(
          request.tableId(), branchId, TableState.OCCUPIED);
    }

    Set<Long> requestedSkuIds =
        request.items().stream()
            .map(PlaceOrderRequest.OrderItemPayload::skuId)
            .collect(Collectors.toSet());

    Map<Long, BigDecimal> officialPrices =
        catalogPriceProvider.getAvailablePrices(branchId, requestedSkuIds);

    BigDecimal totalOrderAmount = BigDecimal.ZERO;

    Set<OrderItem> orderItems =
        request.items().stream()
            .map(
                payload -> {
                  BigDecimal unitPrice = officialPrices.get(payload.skuId());

                  if (unitPrice == null) {
                    throw new ResourceNotFoundException(
                        "El SKU "
                            + payload.skuId()
                            + " is unavailable or does not exist in branch "
                            + branchId);
                  }

                  stockService.deductStock(branchId, payload.skuId(), payload.quantity());

                  return new OrderItem(
                      payload.skuId(), payload.quantity(), unitPrice, payload.comments());
                })
            .collect(Collectors.toSet());

    for (OrderItem item : orderItems) {
      BigDecimal lineTotal =
          item.unitPriceAtPurchase().multiply(BigDecimal.valueOf(item.quantity()));
      totalOrderAmount = totalOrderAmount.add(lineTotal);
    }

    String orderType = request.orderType() != null ? request.orderType() : "DINE_IN";

    CustomerOrder newOrder =
        new CustomerOrder(
            null,
            branchId,
            userId,
            idempotencyKey,
            request.tableId(),
            orderType,
            request.notes(),
            totalOrderAmount,
            "PENDING",
            orderItems);

    Long savedOrderId = orderRepository.save(newOrder).id();

    eventPublisher.publishEvent(
        new OrderPlacedEvent(
            savedOrderId,
            branchId,
            userEmail,
            totalOrderAmount,
            requestedSkuIds,
            request.tableId(),
            resolvedTableName,
            orderType,
            request.notes()));

    return savedOrderId;
  }

  @Transactional(readOnly = true)
  public CustomerOrder getOrderByIdAndBranch(Long orderId, Long branchId) {
    return orderRepository
        .findByIdAndBranchId(orderId, branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Order " + orderId + " not found in branch " + branchId));
  }

  @Transactional(readOnly = true)
  public List<CustomerOrder> getOrdersByBranch(Long branchId) {
    return orderRepository.findAllByBranchId(branchId);
  }
}
