package com.facilcomanda.ponoa.sales;

import com.facilcomanda.ponoa.catalog.CatalogPriceProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.inventory.StockService;
import com.facilcomanda.ponoa.table.RestaurantTableProvider;
import com.facilcomanda.ponoa.table.RestaurantTableView;
import com.facilcomanda.ponoa.table.TableState;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService implements CustomerOrderProvider {

  static final long TABLE_PENDING_ORDER_COOLDOWN_MS = 10_000L;

  private static final Set<String> ALLOWED_ORDER_STATUSES =
      Set.of("PENDING", "IN_PREPARATION", "READY", "DELIVERED", "COMPLETED");

  private static final Set<String> ALLOWED_ITEM_STATUSES =
      Set.of("PENDING", "COMPLETED", "MODIFIED", "CANCELLED");

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

    // 1. Idempotency resolution to prevent duplicates on client retries
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      Optional<CustomerOrder> existingByIdempotency =
          orderRepository.findByBranchIdAndIdempotencyKey(branchId, idempotencyKey.trim());
      if (existingByIdempotency.isPresent()) {
        return existingByIdempotency.get().id();
      }
    }

    String resolvedTableName = null;
    if (request.tableId() != null) {
      // Acquire pessimistic row-level lock on the table row to serialize concurrent order creation
      Optional<RestaurantTableView> lockedTableOpt =
          restaurantTableProvider.lockTableByIdAndBranch(request.tableId(), branchId);
      if (lockedTableOpt.isEmpty()) {
        lockedTableOpt =
            restaurantTableProvider.findTableByIdAndBranch(request.tableId(), branchId);
      }
      RestaurantTableView table =
          lockedTableOpt.orElseThrow(
              () ->
                  new ResourceNotFoundException(
                      "Table " + request.tableId() + " not found in branch " + branchId));

      if (TableState.OUT_OF_SERVICE.equals(table.state())) {
        throw new IllegalStateException(
            "Table " + request.tableId() + " is currently OUT_OF_SERVICE");
      }

      // Rule 2: Enforce 10-second cooldown since the latest PENDING order assigned to this table
      Optional<OffsetDateTime> latestPendingCreatedAt =
          orderRepository.findLatestPendingOrderCreatedAtByTable(branchId, request.tableId());
      if (latestPendingCreatedAt.isPresent()) {
        long elapsedMillis =
            Duration.between(latestPendingCreatedAt.get(), OffsetDateTime.now()).toMillis();
        if (elapsedMillis < TABLE_PENDING_ORDER_COOLDOWN_MS) {
          long remainingSeconds =
              Math.max(
                  1L,
                  (long) Math.ceil((TABLE_PENDING_ORDER_COOLDOWN_MS - elapsedMillis) / 1000.0));
          throw new IllegalStateException(
              "Debe esperar al menos 10 segundos desde la última orden con estado PENDING asignada a la mesa "
                  + table.tableName()
                  + " (restan "
                  + remainingSeconds
                  + " s).");
        }
      }

      // Rule 1: A table can have N open orders simultaneously.
      // If AVAILABLE, occupy atomically; if already OCCUPIED, allow additional order;
      // if BILL_REQUESTED, transition back to OCCUPIED.
      if (TableState.AVAILABLE.equals(table.state())) {
        boolean occupied =
            restaurantTableProvider.occupyTableAtomically(request.tableId(), branchId);
        if (!occupied) {
          throw new IllegalStateException(
              "La mesa " + table.tableName() + " ya está ocupada o no se encuentra disponible.");
        }
      } else if (TableState.BILL_REQUESTED.equals(table.state())) {
        restaurantTableProvider.updateTableState(
            request.tableId(), branchId, TableState.OCCUPIED);
      }

      resolvedTableName =
          table.floorName() != null
              ? (table.zoneName() != null && !table.zoneName().isBlank()
                  ? table.tableName() + " (" + table.floorName() + " - " + table.zoneName() + ")"
                  : table.tableName() + " (" + table.floorName() + ")")
              : table.tableName();
    }

    Set<Long> requestedSkuIds =
        request.items().stream()
            .map(PlaceOrderRequest.OrderItemPayload::skuId)
            .collect(Collectors.toSet());

    Map<Long, BigDecimal> officialPrices =
        catalogPriceProvider.getAvailablePrices(branchId, requestedSkuIds);
    Map<Long, Boolean> requiresStockMap =
        catalogPriceProvider.getRequiresStockForSkus(requestedSkuIds);

    Map<Long, OrderItem> itemsMap = new LinkedHashMap<>();
    for (PlaceOrderRequest.OrderItemPayload payload : request.items()) {
      BigDecimal unitPrice = officialPrices.get(payload.skuId());

      if (unitPrice == null) {
        throw new ResourceNotFoundException(
            "El SKU "
                + payload.skuId()
                + " is unavailable or does not exist in branch "
                + branchId);
      }

      boolean requiresStock =
          requiresStockMap != null && requiresStockMap.getOrDefault(payload.skuId(), true);
      if (requiresStock) {
        stockService.deductStock(branchId, payload.skuId(), payload.quantity());
      }

      if (itemsMap.containsKey(payload.skuId())) {
        OrderItem existing = itemsMap.get(payload.skuId());
        int updatedQty = existing.quantity() + payload.quantity();
        String updatedComments =
            (existing.comments() != null && !existing.comments().isBlank())
                ? (payload.comments() != null && !payload.comments().isBlank())
                    ? existing.comments() + " | " + payload.comments()
                    : existing.comments()
                : payload.comments();
        itemsMap.put(
            payload.skuId(),
            new OrderItem(payload.skuId(), updatedQty, unitPrice, updatedComments, "PENDING"));
      } else {
        itemsMap.put(
            payload.skuId(),
            new OrderItem(
                payload.skuId(), payload.quantity(), unitPrice, payload.comments(), "PENDING"));
      }
    }

    Set<OrderItem> orderItems = new HashSet<>(itemsMap.values());
    BigDecimal totalOrderAmount = BigDecimal.ZERO;

    for (OrderItem item : orderItems) {
      totalOrderAmount = totalOrderAmount.add(item.subtotal());
    }

    String orderType = request.orderType() != null ? request.orderType() : "DINE_IN";
    OffsetDateTime now = OffsetDateTime.now();

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
            null,
            null,
            null,
            now,
            now,
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
    return getOrdersByBranch(branchId, null);
  }

  @Transactional(readOnly = true)
  public List<CustomerOrder> getOrdersByBranch(Long branchId, String status) {
    if (status != null && !status.isBlank()) {
      return orderRepository.findAllByBranchIdAndStatusOrderByIdDesc(
          branchId, status.trim().toUpperCase());
    }
    return orderRepository.findAllByBranchIdOrderByIdDesc(branchId);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> getEnrichedOrdersByBranch(Long branchId) {
    return getEnrichedOrdersByBranch(branchId, null);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> getEnrichedOrdersByBranch(Long branchId, String status) {
    List<CustomerOrder> orders = getOrdersByBranch(branchId, status);
    return mapOrdersToResponses(branchId, orders);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> getActiveOrdersByTable(Long branchId, Long tableId) {
    List<CustomerOrder> orders = orderRepository.findActiveOrdersByTableId(branchId, tableId);
    return mapOrdersToResponses(branchId, orders);
  }

  private List<OrderResponse> mapOrdersToResponses(Long branchId, List<CustomerOrder> orders) {
    if (orders == null || orders.isEmpty()) {
      return List.of();
    }

    Set<Long> allSkuIds =
        orders.stream()
            .filter(o -> o.items() != null)
            .flatMap(o -> o.items().stream())
            .map(OrderItem::skuId)
            .collect(Collectors.toSet());

    Map<Long, String> skuNames = catalogPriceProvider.getSkuNames(allSkuIds);

    Set<Long> tableIds =
        orders.stream()
            .map(CustomerOrder::tableId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, String> tableNames = restaurantTableProvider.getTableNames(tableIds, branchId);

    return orders.stream()
        .map(o -> mapToOrderResponse(o, skuNames, tableNames.get(o.tableId())))
        .toList();
  }

  private OrderResponse mapToOrderResponse(CustomerOrder order) {
    Set<Long> skuIds =
        order.items() != null
            ? order.items().stream().map(OrderItem::skuId).collect(Collectors.toSet())
            : Set.of();
    Map<Long, String> skuNames = catalogPriceProvider.getSkuNames(skuIds);
    String tableName = resolveTableName(order.branchId(), order.tableId());
    return mapToOrderResponse(order, skuNames, tableName);
  }

  private OrderResponse mapToOrderResponse(
      CustomerOrder order, Map<Long, String> skuNames, String tableName) {

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
                            item.comments(),
                            item.status()))
                .toList()
            : List.of();

    boolean isModified = false;
    if (order.notes() != null && order.notes().contains("[ADICION]")) {
      isModified = true;
    } else if (order.items() != null
        && order.items().stream()
            .anyMatch(
                i ->
                    i.isModified()
                        || (i.comments() != null && i.comments().contains("[ADICION]")))) {
      isModified = true;
    }

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
        order.createdAt(),
        order.updatedAt(),
        isModified,
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

    // Rule 4: Allow adding items to PENDING or COMPLETED orders; adding items transitions order back to PENDING
    if (!"PENDING".equalsIgnoreCase(existingOrder.status())
        && !"COMPLETED".equalsIgnoreCase(existingOrder.status())) {
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

    // Deduct stock atomically for added items that require stock control
    Map<Long, Boolean> requiresStockMap =
        catalogPriceProvider.getRequiresStockForSkus(addedSkuIds);
    for (AddOrderItemsRequest.OrderItemPayload item : request.items()) {
      boolean requiresStock =
          requiresStockMap != null && requiresStockMap.getOrDefault(item.skuId(), true);
      if (requiresStock) {
        stockService.deductStock(branchId, item.skuId(), item.quantity());
      }
    }

    // Merge items into linked map
    Map<Long, OrderItem> mergedItemsMap = new LinkedHashMap<>();
    if (existingOrder.items() != null) {
      for (OrderItem item : existingOrder.items()) {
        mergedItemsMap.put(item.skuId(), item);
      }
    }

    Set<OrderItemsAddedEvent.AddedItemSummary> addedSummaries = new HashSet<>();

    for (AddOrderItemsRequest.OrderItemPayload addedItem : request.items()) {
      BigDecimal unitPrice = priceMap.get(addedItem.skuId());

      addedSummaries.add(
          new OrderItemsAddedEvent.AddedItemSummary(
              addedItem.skuId(), addedItem.quantity(), addedItem.comments()));

      if (mergedItemsMap.containsKey(addedItem.skuId())) {
        OrderItem existing = mergedItemsMap.get(addedItem.skuId());
        int baseQty = existing.isCancelled() ? 0 : existing.quantity();
        int updatedQty = baseQty + addedItem.quantity();
        String updatedComments =
            (!existing.isCancelled()
                    && existing.comments() != null
                    && !existing.comments().isBlank())
                ? (addedItem.comments() != null && !addedItem.comments().isBlank())
                    ? existing.comments() + " | [ADICION] " + addedItem.comments()
                    : existing.comments()
                : addedItem.comments();
        // Rule 3: Editing/adding to an existing item transitions it to MODIFIED (requires attention again)
        mergedItemsMap.put(
            addedItem.skuId(),
            new OrderItem(addedItem.skuId(), updatedQty, unitPrice, updatedComments, "MODIFIED"));
      } else {
        mergedItemsMap.put(
            addedItem.skuId(),
            new OrderItem(
                addedItem.skuId(),
                addedItem.quantity(),
                unitPrice,
                addedItem.comments(),
                "PENDING"));
      }
    }

    String newNotes = existingOrder.notes();
    if (request.additionNotes() != null && !request.additionNotes().isBlank()) {
      newNotes =
          (newNotes != null && !newNotes.isBlank())
              ? newNotes + " | [ADICION] " + request.additionNotes()
              : "[ADICION] " + request.additionNotes();
    }

    recalculateAndSaveOrder(existingOrder, new HashSet<>(mergedItemsMap.values()), newNotes);

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

  /**
   * Rule 3 & Rule 4: Edits an existing item in the order, transitions the item status to MODIFIED
   * (requiring attention again), adjusts inventory stock for any quantity delta, and recalculates
   * the order status (back to PENDING) and total amount in the same transaction.
   */
  @Transactional
  public void updateOrderItem(
      Long branchId, Long orderId, Long skuId, UpdateOrderItemRequest request) {
    CustomerOrder existingOrder =
        orderRepository
            .findByIdAndBranchId(orderId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    if ("PAID".equalsIgnoreCase(existingOrder.status())
        || "CANCELLED".equalsIgnoreCase(existingOrder.status())) {
      throw new IllegalStateException(
          "No se pueden modificar ítems de una orden con estado " + existingOrder.status());
    }

    if (existingOrder.items() == null || existingOrder.items().isEmpty()) {
      throw new ResourceNotFoundException(
          "Item with SKU " + skuId + " not found in order " + orderId);
    }

    Map<Long, OrderItem> itemsMap = new LinkedHashMap<>();
    for (OrderItem item : existingOrder.items()) {
      itemsMap.put(item.skuId(), item);
    }

    OrderItem targetItem = itemsMap.get(skuId);
    if (targetItem == null) {
      throw new ResourceNotFoundException(
          "Item with SKU " + skuId + " not found in order " + orderId);
    }

    int previousEffectiveQty = targetItem.isCancelled() ? 0 : targetItem.quantity();
    int quantityDelta = request.quantity() - previousEffectiveQty;

    if (quantityDelta != 0) {
      Map<Long, Boolean> requiresStockMap =
          catalogPriceProvider.getRequiresStockForSkus(Set.of(skuId));
      boolean requiresStock =
          requiresStockMap != null && requiresStockMap.getOrDefault(skuId, true);
      if (requiresStock) {
        if (quantityDelta > 0) {
          stockService.deductStock(branchId, skuId, quantityDelta);
        } else {
          stockService.addStock(branchId, skuId, -quantityDelta);
        }
      }
    }

    OrderItem modifiedItem =
        new OrderItem(
            skuId,
            request.quantity(),
            targetItem.unitPriceAtPurchase(),
            request.comments() != null ? request.comments() : targetItem.comments(),
            "MODIFIED");
    itemsMap.put(skuId, modifiedItem);

    recalculateAndSaveOrder(
        existingOrder, new HashSet<>(itemsMap.values()), existingOrder.notes());
  }

  /**
   * Marks a specific item as COMPLETED (attended by MESERO) and recalculates the parent order
   * status in the same transaction.
   */
  @Transactional
  public void attendOrderItem(Long branchId, Long orderId, Long skuId) {
    updateOrderItemStatus(branchId, orderId, skuId, "COMPLETED");
  }

  /**
   * Cancels a specific item in the order, replenishes its stock if applicable, and recalculates
   * the parent order status and total in the same transaction.
   */
  @Transactional
  public void cancelOrderItem(Long branchId, Long orderId, Long skuId) {
    updateOrderItemStatus(branchId, orderId, skuId, "CANCELLED");
  }

  /**
   * Updates an individual order item's status (PENDING, COMPLETED, MODIFIED, CANCELLED) and
   * recalculates the order status and total amount in the same transaction.
   */
  @Transactional
  public void updateOrderItemStatus(
      Long branchId, Long orderId, Long skuId, String newItemStatus) {
    if (newItemStatus == null || newItemStatus.isBlank()) {
      throw new IllegalArgumentException("El estado del ítem no puede ser nulo o vacío.");
    }

    String normalizedItemStatus = newItemStatus.trim().toUpperCase();
    if (!ALLOWED_ITEM_STATUSES.contains(normalizedItemStatus)) {
      throw new IllegalArgumentException(
          "Estado de ítem no válido: "
              + newItemStatus
              + ". Estados permitidos: "
              + ALLOWED_ITEM_STATUSES);
    }

    CustomerOrder existingOrder =
        orderRepository
            .findByIdAndBranchId(orderId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    if ("PAID".equalsIgnoreCase(existingOrder.status())
        || "CANCELLED".equalsIgnoreCase(existingOrder.status())) {
      throw new IllegalStateException(
          "No se puede cambiar el estado de un ítem en una orden con estado "
              + existingOrder.status());
    }

    if (existingOrder.items() == null || existingOrder.items().isEmpty()) {
      throw new ResourceNotFoundException(
          "Item with SKU " + skuId + " not found in order " + orderId);
    }

    Map<Long, OrderItem> itemsMap = new LinkedHashMap<>();
    for (OrderItem item : existingOrder.items()) {
      itemsMap.put(item.skuId(), item);
    }

    OrderItem targetItem = itemsMap.get(skuId);
    if (targetItem == null) {
      throw new ResourceNotFoundException(
          "Item with SKU " + skuId + " not found in order " + orderId);
    }

    boolean wasCancelled = targetItem.isCancelled();
    boolean willBeCancelled = "CANCELLED".equalsIgnoreCase(normalizedItemStatus);

    if (!wasCancelled && willBeCancelled) {
      Map<Long, Boolean> requiresStockMap =
          catalogPriceProvider.getRequiresStockForSkus(Set.of(skuId));
      boolean requiresStock =
          requiresStockMap != null && requiresStockMap.getOrDefault(skuId, true);
      if (requiresStock) {
        stockService.addStock(branchId, skuId, targetItem.quantity());
      }
    } else if (wasCancelled && !willBeCancelled) {
      Map<Long, Boolean> requiresStockMap =
          catalogPriceProvider.getRequiresStockForSkus(Set.of(skuId));
      boolean requiresStock =
          requiresStockMap != null && requiresStockMap.getOrDefault(skuId, true);
      if (requiresStock) {
        stockService.deductStock(branchId, skuId, targetItem.quantity());
      }
    }

    itemsMap.put(
        skuId,
        new OrderItem(
            targetItem.skuId(),
            targetItem.quantity(),
            targetItem.unitPriceAtPurchase(),
            targetItem.comments(),
            normalizedItemStatus));

    recalculateAndSaveOrder(
        existingOrder, new HashSet<>(itemsMap.values()), existingOrder.notes());
  }

  /**
   * Recalculates order totalAmount and status from its current items within the active transaction:
   * - Order becomes COMPLETED if and only if all active (non-CANCELLED) items are COMPLETED.
   * - Order becomes PENDING if any active item is PENDING or MODIFIED.
   * - Order becomes CANCELLED if all items are CANCELLED (releasing table if no active orders remain).
   */
  private void recalculateAndSaveOrder(
      CustomerOrder existingOrder, Set<OrderItem> updatedItems, String updatedNotes) {
    List<OrderItem> activeItems =
        updatedItems.stream().filter(item -> !item.isCancelled()).toList();

    BigDecimal newTotalAmount = BigDecimal.ZERO;
    for (OrderItem item : activeItems) {
      newTotalAmount = newTotalAmount.add(item.subtotal());
    }

    String newOrderStatus;
    if (activeItems.isEmpty()) {
      newOrderStatus = "CANCELLED";
    } else if (activeItems.stream().allMatch(OrderItem::isCompleted)) {
      newOrderStatus = "COMPLETED";
    } else {
      newOrderStatus = "PENDING";
    }

    CustomerOrder updatedOrder =
        new CustomerOrder(
            existingOrder.id(),
            existingOrder.branchId(),
            existingOrder.appUserId(),
            existingOrder.idempotencyKey(),
            existingOrder.tableId(),
            existingOrder.orderType(),
            updatedNotes,
            newTotalAmount,
            newOrderStatus,
            existingOrder.cancelledBy(),
            existingOrder.cancellationReason(),
            "CANCELLED".equalsIgnoreCase(newOrderStatus)
                ? OffsetDateTime.now()
                : existingOrder.cancelledAt(),
            existingOrder.createdAt(),
            OffsetDateTime.now(),
            updatedItems);

    orderRepository.save(updatedOrder);

    if ("CANCELLED".equalsIgnoreCase(newOrderStatus) && existingOrder.tableId() != null) {
      releaseTableIfNoActiveOrdersRemain(existingOrder.branchId(), existingOrder.tableId());
    }

    if (!Objects.equals(existingOrder.status(), newOrderStatus)) {
      eventPublisher.publishEvent(
          new OrderStatusChangedEvent(
              existingOrder.id(),
              existingOrder.branchId(),
              existingOrder.status(),
              newOrderStatus));
    }
  }

  private void releaseTableIfNoActiveOrdersRemain(Long branchId, Long tableId) {
    if (tableId == null) {
      return;
    }
    long activeOrders = orderRepository.countActiveOrdersByTableId(branchId, tableId);
    if (activeOrders == 0) {
      restaurantTableProvider.updateTableState(tableId, branchId, TableState.AVAILABLE);
    }
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
                      ? order.items().stream()
                          .filter(item -> !item.isCancelled())
                          .map(OrderItem::skuId)
                          .collect(Collectors.toSet())
                      : Set.of();

              Map<Long, String> skuNames = catalogPriceProvider.getSkuNames(skuIds);
              Map<Long, String> areaMap = catalogPriceProvider.getProductionAreasForSkus(skuIds);

              List<CustomerOrderProvider.OrderPrintItem> printItems =
                  order.items() != null
                      ? order.items().stream()
                          .filter(item -> !item.isCancelled())
                          .map(
                              item ->
                                  new CustomerOrderProvider.OrderPrintItem(
                                      item.skuId(),
                                      skuNames != null
                                          ? skuNames.getOrDefault(
                                              item.skuId(), "SKU-" + item.skuId())
                                          : "SKU-" + item.skuId(),
                                      item.quantity(),
                                      areaMap != null
                                          ? areaMap.getOrDefault(item.skuId(), "COCINA")
                                          : "COCINA",
                                      item.comments()))
                          .toList()
                      : List.of();

              return new CustomerOrderProvider.OrderPrintSummary(
                  order.id(),
                  order.branchId(),
                  order.tableId(),
                  tableName,
                  order.orderType(),
                  order.notes(),
                  skuIds,
                  printItems);
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

  @Transactional
  public void updateOrderStatus(Long branchId, Long orderId, String newStatus) {
    if (newStatus == null || newStatus.isBlank()) {
      throw new IllegalArgumentException("El estado no puede ser nulo o vacío.");
    }

    String normalizedStatus = newStatus.trim().toUpperCase();

    if ("PAID".equalsIgnoreCase(normalizedStatus)) {
      throw new IllegalArgumentException(
          "Para marcar una orden como pagada utilice el flujo de cobro / facturación correspondiente.");
    }
    if ("CANCELLED".equalsIgnoreCase(normalizedStatus)) {
      throw new IllegalArgumentException(
          "Para cancelar una orden utilice el endpoint de cancelación (/cancel) indicando el motivo.");
    }
    if (!ALLOWED_ORDER_STATUSES.contains(normalizedStatus)) {
      throw new IllegalArgumentException(
          "Estado no válido: " + newStatus + ". Estados permitidos: " + ALLOWED_ORDER_STATUSES);
    }

    CustomerOrder order =
        orderRepository
            .findByIdAndBranchId(orderId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    if ("PAID".equalsIgnoreCase(order.status())) {
      throw new IllegalStateException("No se puede cambiar el estado de una orden ya PAGADA.");
    }
    if ("CANCELLED".equalsIgnoreCase(order.status())) {
      throw new IllegalStateException("No se puede cambiar el estado de una orden ya CANCELADA.");
    }

    orderRepository.updateStatus(orderId, branchId, normalizedStatus);

    eventPublisher.publishEvent(
        new OrderStatusChangedEvent(orderId, branchId, order.status(), normalizedStatus));
  }

  @Transactional
  public void markOrderAsDelivered(Long branchId, Long orderId) {
    updateOrderStatus(branchId, orderId, "DELIVERED");
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

    // Rule 5: Release table to AVAILABLE only when all orders on the table are PAID or CANCELLED
    releaseTableIfNoActiveOrdersRemain(branchId, order.tableId());
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
          "Order "
              + orderId
              + " is already PAID and cannot be directly cancelled. Issue a refund/credit note instead.");
    }

    orderRepository.cancelOrder(orderId, branchId, cancelledBy, reason);

    // 1. Revert inventory (replenish stock for active order items that require stock control)
    if (order.items() != null && !order.items().isEmpty()) {
      List<OrderItem> activeItems =
          order.items().stream().filter(item -> !item.isCancelled()).toList();
      if (!activeItems.isEmpty()) {
        Set<Long> itemSkuIds =
            activeItems.stream().map(OrderItem::skuId).collect(Collectors.toSet());
        Map<Long, Boolean> requiresStockMap =
            catalogPriceProvider.getRequiresStockForSkus(itemSkuIds);
        for (OrderItem item : activeItems) {
          boolean requiresStock =
              requiresStockMap != null && requiresStockMap.getOrDefault(item.skuId(), true);
          if (requiresStock) {
            stockService.addStock(branchId, item.skuId(), item.quantity());
          }
        }
      }
    }

    // 2. Rule 5: Release table only if no other active orders remain on that table
    releaseTableIfNoActiveOrdersRemain(branchId, order.tableId());

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
                        "No active pending order found for table "
                            + tableId
                            + " in branch "
                            + branchId));

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
                        "No active pending order found for table "
                            + tableId
                            + " in branch "
                            + branchId));

    eventPublisher.publishEvent(new PreCheckRequestedEvent(order.id(), branchId, targetPrinterId));
  }

  private PreCheckResponse buildPreCheckResponse(CustomerOrder order) {
    String tableName = resolveTableName(order.branchId(), order.tableId());

    Set<Long> skuIds =
        order.items() != null
            ? order.items().stream()
                .filter(item -> !item.isCancelled())
                .map(OrderItem::skuId)
                .collect(Collectors.toSet())
            : Set.of();

    Map<Long, String> skuNames = catalogPriceProvider.getSkuNames(skuIds);

    List<PreCheckResponse.PreCheckItemDto> items =
        order.items() != null
            ? order.items().stream()
                .filter(item -> !item.isCancelled())
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
    BigDecimal suggestedTip10 =
        total.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);

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

  private CustomerOrderProvider.OrderPreCheckSummary buildOrderPreCheckSummary(
      CustomerOrder order) {
    String tableName = resolveTableName(order.branchId(), order.tableId());

    Set<Long> skuIds =
        order.items() != null
            ? order.items().stream()
                .filter(item -> !item.isCancelled())
                .map(OrderItem::skuId)
                .collect(Collectors.toSet())
            : Set.of();

    Map<Long, String> skuNames = catalogPriceProvider.getSkuNames(skuIds);

    List<CustomerOrderProvider.PreCheckItem> items =
        order.items() != null
            ? order.items().stream()
                .filter(item -> !item.isCancelled())
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
    BigDecimal suggestedTip10 =
        total.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);

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
