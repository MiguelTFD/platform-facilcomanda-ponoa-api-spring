package com.facilcomanda.ponoa.sales;

import com.facilcomanda.ponoa.catalog.CatalogPriceProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.inventory.StockService;
import com.facilcomanda.ponoa.table.RestaurantTableProvider;
import com.facilcomanda.ponoa.table.RestaurantTableView;
import com.facilcomanda.ponoa.table.TableState;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService implements CustomerOrderProvider {

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

      boolean occupied =
          restaurantTableProvider.occupyTableAtomically(request.tableId(), branchId);
      if (!occupied) {
        throw new IllegalStateException(
            "La mesa " + table.tableName() + " ya está ocupada o no se encuentra disponible.");
      }

      resolvedTableName =
          table.floorName() != null
              ? table.tableName() + " (" + table.floorName() + ")"
              : table.tableName();
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

  @Transactional
  public void addItemsToOrder(Long branchId, Long orderId, AddOrderItemsRequest request) {
    CustomerOrder existingOrder =
        orderRepository
            .findByIdAndBranchId(orderId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    if (!"PENDING".equalsIgnoreCase(existingOrder.status())) {
      throw new IllegalStateException(
          "No se pueden agregar items a una orden con estado " + existingOrder.status());
    }

    Set<Long> addedSkuIds =
        request.items().stream()
            .map(AddOrderItemsRequest.OrderItemPayload::skuId)
            .collect(Collectors.toSet());

    Map<Long, BigDecimal> priceMap = catalogPriceProvider.getAvailablePrices(branchId, addedSkuIds);
    for (Long skuId : addedSkuIds) {
      if (!priceMap.containsKey(skuId)) {
        throw new ResourceNotFoundException(
            "Product SKU " + skuId + " is unavailable or inactive in branch " + branchId);
      }
    }

    // Deduct stock atomically for added items
    for (AddOrderItemsRequest.OrderItemPayload item : request.items()) {
      stockService.deductStock(branchId, item.skuId(), item.quantity());
    }

    // Merge items into linked map
    Map<Long, OrderItem> mergedItemsMap = new LinkedHashMap<>();
    if (existingOrder.items() != null) {
      for (OrderItem item : existingOrder.items()) {
        mergedItemsMap.put(item.skuId(), item);
      }
    }

    BigDecimal additionTotal = BigDecimal.ZERO;
    Set<OrderItemsAddedEvent.AddedItemSummary> addedSummaries = new HashSet<>();

    for (AddOrderItemsRequest.OrderItemPayload addedItem : request.items()) {
      BigDecimal unitPrice = priceMap.get(addedItem.skuId());
      BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(addedItem.quantity()));
      additionTotal = additionTotal.add(lineTotal);

      addedSummaries.add(
          new OrderItemsAddedEvent.AddedItemSummary(
              addedItem.skuId(), addedItem.quantity(), addedItem.comments()));

      if (mergedItemsMap.containsKey(addedItem.skuId())) {
        OrderItem existing = mergedItemsMap.get(addedItem.skuId());
        int updatedQty = existing.quantity() + addedItem.quantity();
        String updatedComments =
            (existing.comments() != null && !existing.comments().isBlank())
                ? (addedItem.comments() != null && !addedItem.comments().isBlank())
                    ? existing.comments() + " | [ADICION] " + addedItem.comments()
                    : existing.comments()
                : addedItem.comments();
        mergedItemsMap.put(
            addedItem.skuId(),
            new OrderItem(addedItem.skuId(), updatedQty, unitPrice, updatedComments));
      } else {
        mergedItemsMap.put(
            addedItem.skuId(),
            new OrderItem(
                addedItem.skuId(),
                addedItem.quantity(),
                unitPrice,
                addedItem.comments()));
      }
    }

    BigDecimal newTotalAmount = existingOrder.totalAmount().add(additionTotal);

    String newNotes = existingOrder.notes();
    if (request.additionNotes() != null && !request.additionNotes().isBlank()) {
      newNotes =
          (newNotes != null && !newNotes.isBlank())
              ? newNotes + " | [ADICION] " + request.additionNotes()
              : "[ADICION] " + request.additionNotes();
    }

    CustomerOrder updatedOrder =
        new CustomerOrder(
            existingOrder.id(),
            existingOrder.branchId(),
            existingOrder.appUserId(),
            existingOrder.idempotencyKey(),
            existingOrder.tableId(),
            existingOrder.orderType(),
            newNotes,
            newTotalAmount,
            existingOrder.status(),
            new HashSet<>(mergedItemsMap.values()));

    orderRepository.save(updatedOrder);

    String resolvedTableName = null;
    if (existingOrder.tableId() != null) {
      resolvedTableName =
          restaurantTableProvider
              .findTableByIdAndBranch(existingOrder.tableId(), branchId)
              .map(RestaurantTableView::tableName)
              .orElse(null);
    }

    eventPublisher.publishEvent(
        new OrderItemsAddedEvent(
            branchId,
            orderId,
            existingOrder.tableId(),
            resolvedTableName,
            existingOrder.orderType(),
            request.additionNotes(),
            addedSummaries,
            addedSkuIds));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerOrderProvider.OrderPrintSummary> getOrderPrintSummary(
      Long branchId, Long orderId) {
    return orderRepository
        .findByIdAndBranchId(orderId, branchId)
        .map(
            order -> {
              String tableName = null;
              if (order.tableId() != null) {
                tableName =
                    restaurantTableProvider
                        .findTableByIdAndBranch(order.tableId(), branchId)
                        .map(RestaurantTableView::tableName)
                        .orElse(null);
              }
              Set<Long> skuIds =
                  order.items() != null
                      ? order.items().stream().map(OrderItem::skuId).collect(Collectors.toSet())
                      : Set.of();

              return new CustomerOrderProvider.OrderPrintSummary(
                  order.id(),
                  order.branchId(),
                  order.tableId(),
                  tableName,
                  order.orderType(),
                  order.notes(),
                  skuIds);
            });
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerOrderProvider.OrderBillingSummary> getOrderBillingSummary(
      Long branchId, Long orderId) {
    return orderRepository
        .findByIdAndBranchId(orderId, branchId)
        .map(
            order -> {
              String tableName = null;
              if (order.tableId() != null) {
                tableName =
                    restaurantTableProvider
                        .findTableByIdAndBranch(order.tableId(), branchId)
                        .map(RestaurantTableView::tableName)
                        .orElse(null);
              }

              return new CustomerOrderProvider.OrderBillingSummary(
                  order.id(),
                  order.branchId(),
                  order.tableId(),
                  tableName,
                  order.orderType(),
                  order.totalAmount(),
                  order.status());
            });
  }

  @Override
  @Transactional
  public void markOrderAsPaid(Long branchId, Long orderId) {
    CustomerOrder order =
        orderRepository
            .findByIdAndBranchId(orderId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    orderRepository.updateStatus(orderId, branchId, "PAID");

    if (order.tableId() != null) {
      restaurantTableProvider.updateTableState(order.tableId(), branchId, TableState.AVAILABLE);
    }
  }
}
