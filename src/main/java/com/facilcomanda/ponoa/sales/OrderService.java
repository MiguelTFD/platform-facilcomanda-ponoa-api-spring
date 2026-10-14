package com.facilcomanda.ponoa.sales;

import com.facilcomanda.ponoa.catalog.CatalogPriceProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.inventory.StockService;
import com.facilcomanda.ponoa.table.RestaurantTableProvider;
import com.facilcomanda.ponoa.table.RestaurantTableView;
import com.facilcomanda.ponoa.table.TableState;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
  public OrderResponse getEnrichedOrderByIdAndBranch(Long orderId, Long branchId) {
    CustomerOrder order = getOrderByIdAndBranch(orderId, branchId);
    return mapToOrderResponse(order);
  }

  @Transactional(readOnly = true)
  public List<CustomerOrder> getOrdersByBranch(Long branchId) {
    return orderRepository.findAllByBranchId(branchId);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> getEnrichedOrdersByBranch(Long branchId) {
    List<CustomerOrder> orders = orderRepository.findAllByBranchId(branchId);
    if (orders.isEmpty()) {
      return List.of();
    }

    Set<Long> allSkuIds =
        orders.stream()
            .filter(o -> o.items() != null)
            .flatMap(o -> o.items().stream())
            .map(OrderItem::skuId)
            .collect(Collectors.toSet());

    Map<Long, String> skuNames = catalogPriceProvider.getSkuNames(allSkuIds);

    return orders.stream()
        .map(o -> mapToOrderResponse(o, skuNames))
        .toList();
  }

  private OrderResponse mapToOrderResponse(CustomerOrder order) {
    Set<Long> skuIds =
        order.items() != null
            ? order.items().stream().map(OrderItem::skuId).collect(Collectors.toSet())
            : Set.of();
    Map<Long, String> skuNames = catalogPriceProvider.getSkuNames(skuIds);
    return mapToOrderResponse(order, skuNames);
  }

  private OrderResponse mapToOrderResponse(CustomerOrder order, Map<Long, String> skuNames) {
    String tableName = resolveTableName(order.branchId(), order.tableId());

    List<OrderResponse.OrderItemResponse> items =
        order.items() != null
            ? order.items().stream()
                .map(
                    item ->
                        new OrderResponse.OrderItemResponse(
                            item.skuId(),
                            skuNames.getOrDefault(item.skuId(), "SKU-" + item.skuId()),
                            item.quantity(),
                            item.unitPriceAtPurchase(),
                            item.subtotal(),
                            item.comments()))
                .toList()
            : List.of();

    return new OrderResponse(
        order.id(),
        order.branchId(),
        order.appUserId(),
        order.idempotencyKey(),
        order.tableId(),
        tableName,
        order.orderType(),
        order.notes(),
        order.totalAmount(),
        order.status(),
        order.cancelledBy(),
        order.cancellationReason(),
        order.cancelledAt(),
        items);
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

  @Transactional
  public void cancelOrder(Long branchId, Long orderId, UUID cancelledBy, String reason) {
    CustomerOrder order =
        orderRepository
            .findByIdAndBranchId(orderId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    if ("CANCELLED".equalsIgnoreCase(order.status())) {
      throw new IllegalStateException("Order " + orderId + " is already CANCELLED.");
    }

    if ("PAID".equalsIgnoreCase(order.status())) {
      throw new IllegalStateException(
          "Order " + orderId + " is already PAID and cannot be directly cancelled. Issue a refund/credit note instead.");
    }

    orderRepository.cancelOrder(orderId, branchId, cancelledBy, reason);

    // 1. Revert inventory (replenish stock for all order items)
    if (order.items() != null) {
      for (OrderItem item : order.items()) {
        stockService.addStock(branchId, item.skuId(), item.quantity());
      }
    }

    // 2. Release table if assigned
    if (order.tableId() != null) {
      restaurantTableProvider.updateTableState(order.tableId(), branchId, TableState.AVAILABLE);
    }

    // 3. Publish domain event to notify kitchen and other listeners
    eventPublisher.publishEvent(
        new OrderCancelledEvent(orderId, branchId, reason, cancelledBy));
  }

  @Transactional(readOnly = true)
  public PreCheckResponse getOrderPreCheck(Long branchId, Long orderId) {
    CustomerOrder order =
        orderRepository
            .findByIdAndBranchId(orderId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    return buildPreCheckResponse(order);
  }

  @Transactional(readOnly = true)
  public PreCheckResponse getOrderPreCheckByTable(Long branchId, Long tableId) {
    CustomerOrder order =
        orderRepository
            .findActiveOrderByTableId(branchId, tableId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No active pending order found for table " + tableId + " in branch " + branchId));

    return buildPreCheckResponse(order);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerOrderProvider.OrderPreCheckSummary> getOrderPreCheckSummary(
      Long branchId, Long orderId) {
    return orderRepository
        .findByIdAndBranchId(orderId, branchId)
        .map(this::buildOrderPreCheckSummary);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerOrderProvider.OrderPreCheckSummary> getActiveOrderPreCheckSummaryByTable(
      Long branchId, Long tableId) {
    return orderRepository
        .findActiveOrderByTableId(branchId, tableId)
        .map(this::buildOrderPreCheckSummary);
  }

  @Transactional
  public void requestPreCheckPrint(Long branchId, Long orderId, Long targetPrinterId) {
    CustomerOrder order =
        orderRepository
            .findByIdAndBranchId(orderId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    eventPublisher.publishEvent(new PreCheckRequestedEvent(order.id(), branchId, targetPrinterId));
  }

  @Transactional
  public void requestPreCheckPrintByTable(Long branchId, Long tableId, Long targetPrinterId) {
    CustomerOrder order =
        orderRepository
            .findActiveOrderByTableId(branchId, tableId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No active pending order found for table " + tableId + " in branch " + branchId));

    eventPublisher.publishEvent(new PreCheckRequestedEvent(order.id(), branchId, targetPrinterId));
  }

  private PreCheckResponse buildPreCheckResponse(CustomerOrder order) {
    String tableName = resolveTableName(order.branchId(), order.tableId());

    Set<Long> skuIds =
        order.items() != null
            ? order.items().stream().map(OrderItem::skuId).collect(Collectors.toSet())
            : Set.of();

    Map<Long, String> skuNames = catalogPriceProvider.getSkuNames(skuIds);

    List<PreCheckResponse.PreCheckItemDto> items =
        order.items() != null
            ? order.items().stream()
                .map(
                    item ->
                        new PreCheckResponse.PreCheckItemDto(
                            item.skuId(),
                            skuNames.getOrDefault(item.skuId(), "SKU-" + item.skuId()),
                            item.quantity(),
                            item.unitPriceAtPurchase(),
                            item.subtotal(),
                            item.comments()))
                .toList()
            : List.of();

    BigDecimal total = order.totalAmount() != null ? order.totalAmount() : BigDecimal.ZERO;
    BigDecimal subtotal = total.divide(BigDecimal.valueOf(1.18), 2, RoundingMode.HALF_UP);
    BigDecimal taxAmount = total.subtract(subtotal);
    BigDecimal suggestedTip10 = total.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);

    return new PreCheckResponse(
        order.id(),
        order.branchId(),
        order.tableId(),
        tableName,
        order.orderType(),
        subtotal,
        taxAmount,
        total,
        suggestedTip10,
        items);
  }

  private CustomerOrderProvider.OrderPreCheckSummary buildOrderPreCheckSummary(CustomerOrder order) {
    String tableName = resolveTableName(order.branchId(), order.tableId());

    Set<Long> skuIds =
        order.items() != null
            ? order.items().stream().map(OrderItem::skuId).collect(Collectors.toSet())
            : Set.of();

    Map<Long, String> skuNames = catalogPriceProvider.getSkuNames(skuIds);

    List<CustomerOrderProvider.PreCheckItem> items =
        order.items() != null
            ? order.items().stream()
                .map(
                    item ->
                        new CustomerOrderProvider.PreCheckItem(
                            item.skuId(),
                            skuNames.getOrDefault(item.skuId(), "SKU-" + item.skuId()),
                            item.quantity(),
                            item.unitPriceAtPurchase(),
                            item.subtotal(),
                            item.comments()))
                .toList()
            : List.of();

    BigDecimal total = order.totalAmount() != null ? order.totalAmount() : BigDecimal.ZERO;
    BigDecimal subtotal = total.divide(BigDecimal.valueOf(1.18), 2, RoundingMode.HALF_UP);
    BigDecimal taxAmount = total.subtract(subtotal);
    BigDecimal suggestedTip10 = total.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);

    return new CustomerOrderProvider.OrderPreCheckSummary(
        order.id(),
        order.branchId(),
        order.tableId(),
        tableName,
        order.orderType(),
        null,
        total,
        subtotal,
        taxAmount,
        suggestedTip10,
        items);
  }

  private String resolveTableName(Long branchId, Long tableId) {
    if (tableId == null) return null;
    return restaurantTableProvider
        .findTableByIdAndBranch(tableId, branchId)
        .map(RestaurantTableView::tableName)
        .orElse(null);
  }
}
