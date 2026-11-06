package com.facilcomanda.ponoa.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.catalog.CatalogPriceProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.inventory.StockService;
import com.facilcomanda.ponoa.table.RestaurantTableProvider;
import com.facilcomanda.ponoa.table.RestaurantTableView;
import com.facilcomanda.ponoa.table.TableState;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private CatalogPriceProvider catalogPriceProvider;
  @Mock private StockService stockService;
  @Mock private RestaurantTableProvider restaurantTableProvider;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private OrderService orderService;

  @Test
  @DisplayName("Should place order scoped to branch and publish OrderPlacedEvent with branchId")
  void shouldPlaceOrderSuccessfully() {
    Long branchId = 5L;
    UUID userId = UUID.randomUUID();
    String idempotencyKey = UUID.randomUUID().toString();
    String email = "waiter@facilcomanda.com";

    PlaceOrderRequest request =
        new PlaceOrderRequest(Set.of(new PlaceOrderRequest.OrderItemPayload(101L, 2)));

    when(catalogPriceProvider.getAvailablePrices(branchId, Set.of(101L)))
        .thenReturn(Map.of(101L, new BigDecimal("25.50")));

    CustomerOrder savedOrder =
        new CustomerOrder(
            1L,
            branchId,
            userId,
            idempotencyKey,
            new BigDecimal("51.00"),
            "PENDING",
            Set.of(new OrderItem(101L, 2, new BigDecimal("25.50"))));

    when(orderRepository.save(any(CustomerOrder.class))).thenReturn(savedOrder);

    Long orderId = orderService.placeOrder(branchId, userId, idempotencyKey, email, request);

    assertThat(orderId).isEqualTo(1L);

    ArgumentCaptor<CustomerOrder> orderCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().branchId()).isEqualTo(branchId);
    assertThat(orderCaptor.getValue().totalAmount()).isEqualByComparingTo("51.00");

    ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().branchId()).isEqualTo(branchId);
    assertThat(eventCaptor.getValue().orderId()).isEqualTo(1L);
    assertThat(eventCaptor.getValue().skuIds()).containsExactly(101L);

    verify(stockService).deductStock(branchId, 101L, 2);
  }

  @Test
  @DisplayName("Should get order by id and branch when exists")
  void shouldGetOrderByIdAndBranch() {
    CustomerOrder order =
        new CustomerOrder(
            1L, 5L, UUID.randomUUID(), "key-1", new BigDecimal("50.00"), "PENDING", Set.of());

    when(orderRepository.findByIdAndBranchId(1L, 5L)).thenReturn(Optional.of(order));

    CustomerOrder result = orderService.getOrderByIdAndBranch(1L, 5L);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.branchId()).isEqualTo(5L);
  }

  @Test
  @DisplayName("Should throw ResourceNotFoundException when order does not belong to branch")
  void shouldThrowWhenOrderNotInBranch() {
    when(orderRepository.findByIdAndBranchId(1L, 5L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getOrderByIdAndBranch(1L, 5L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("branch 5");
  }

  @Test
  @DisplayName("Should place DINE_IN order with table, transition table to OCCUPIED and capture item comments")
  void shouldPlaceDineInOrderWithTableAndComments() {
    Long branchId = 2L;
    Long tableId = 15L;
    UUID userId = UUID.randomUUID();
    String idempotencyKey = "order-table-15";

    PlaceOrderRequest request =
        new PlaceOrderRequest(
            tableId,
            "DINE_IN",
            "Traer la cuenta apenas terminen",
            Set.of(
                new PlaceOrderRequest.OrderItemPayload(201L, 1, "Sin cebolla, bien cocido")));

    RestaurantTableView tableView =
        new RestaurantTableView(tableId, branchId, 3L, "Mesa 4", "Terraza", TableState.AVAILABLE);
    when(restaurantTableProvider.findTableByIdAndBranch(tableId, branchId))
        .thenReturn(Optional.of(tableView));
    when(restaurantTableProvider.occupyTableAtomically(tableId, branchId))
        .thenReturn(true);

    when(catalogPriceProvider.getAvailablePrices(branchId, Set.of(201L)))
        .thenReturn(Map.of(201L, new BigDecimal("45.00")));

    CustomerOrder savedOrder =
        new CustomerOrder(
            99L,
            branchId,
            userId,
            idempotencyKey,
            tableId,
            "DINE_IN",
            "Traer la cuenta apenas terminen",
            new BigDecimal("45.00"),
            "PENDING",
            Set.of(new OrderItem(201L, 1, new BigDecimal("45.00"), "Sin cebolla, bien cocido")));

    when(orderRepository.save(any(CustomerOrder.class))).thenReturn(savedOrder);

    Long orderId =
        orderService.placeOrder(branchId, userId, idempotencyKey, "waiter@facilcomanda.com", request);

    assertThat(orderId).isEqualTo(99L);

    // Verify atomic table occupation
    verify(restaurantTableProvider).occupyTableAtomically(tableId, branchId);

    // Verify order saved with table and notes
    ArgumentCaptor<CustomerOrder> orderCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
    verify(orderRepository).save(orderCaptor.capture());
    CustomerOrder capturedOrder = orderCaptor.getValue();
    assertThat(capturedOrder.tableId()).isEqualTo(tableId);
    assertThat(capturedOrder.notes()).isEqualTo("Traer la cuenta apenas terminen");
    assertThat(capturedOrder.items()).hasSize(1);
    OrderItem capturedItem = capturedOrder.items().iterator().next();
    assertThat(capturedItem.comments()).isEqualTo("Sin cebolla, bien cocido");
    assertThat(capturedItem.subtotal()).isEqualByComparingTo("45.00");

    // Verify event published with resolved table name "Mesa 4 (Terraza)"
    ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    OrderPlacedEvent publishedEvent = eventCaptor.getValue();
    assertThat(publishedEvent.tableName()).isEqualTo("Mesa 4 (Terraza)");
    assertThat(publishedEvent.orderType()).isEqualTo("DINE_IN");
    assertThat(publishedEvent.notes()).isEqualTo("Traer la cuenta apenas terminen");
  }

  @Test
  @DisplayName("Should throw IllegalStateException when table is already occupied concurrently by another waiter")
  void shouldThrowWhenTableAlreadyOccupiedConcurrently() {
    Long branchId = 2L;
    Long tableId = 15L;
    PlaceOrderRequest request =
        new PlaceOrderRequest(
            tableId, "DINE_IN", null, Set.of(new PlaceOrderRequest.OrderItemPayload(201L, 1)));

    RestaurantTableView tableView =
        new RestaurantTableView(tableId, branchId, 3L, "Mesa 4", "Terraza", TableState.AVAILABLE);
    when(restaurantTableProvider.findTableByIdAndBranch(tableId, branchId))
        .thenReturn(Optional.of(tableView));
    when(restaurantTableProvider.occupyTableAtomically(tableId, branchId))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                orderService.placeOrder(
                    branchId, UUID.randomUUID(), "key-concurrent", "waiter@facilcomanda.com", request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ya está ocupada o no se encuentra disponible");
  }

  @Test
  @DisplayName("Should return order print summary with resolved table name and skuIds")
  void shouldReturnOrderPrintSummary() {
    Long branchId = 1L;
    Long orderId = 88L;
    Long tableId = 3L;

    CustomerOrder order =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "key-1",
            tableId,
            "DINE_IN",
            "Mesa cerca a ventana",
            new BigDecimal("40.00"),
            "PENDING",
            Set.of(new OrderItem(101L, 2, new BigDecimal("20.00"), "Sin cebolla")));

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(order));
    RestaurantTableView tableView =
        new RestaurantTableView(tableId, branchId, 1L, "Mesa 3", "Salon", TableState.OCCUPIED);
    when(restaurantTableProvider.findTableByIdAndBranch(tableId, branchId))
        .thenReturn(Optional.of(tableView));

    Optional<CustomerOrderProvider.OrderPrintSummary> result =
        orderService.getOrderPrintSummary(branchId, orderId);

    assertThat(result).isPresent();
    CustomerOrderProvider.OrderPrintSummary summary = result.get();
    assertThat(summary.orderId()).isEqualTo(orderId);
    assertThat(summary.tableName()).isEqualTo("Mesa 3");
    assertThat(summary.orderType()).isEqualTo("DINE_IN");
    assertThat(summary.notes()).isEqualTo("Mesa cerca a ventana");
    assertThat(summary.skuIds()).containsExactly(101L);
  }

  @Test
  @DisplayName("Should add incremental items to existing order, merge quantities, deduct stock and publish event")
  void shouldAddItemsToOrderSuccessfully() {
    Long branchId = 1L;
    Long orderId = 10L;
    Long tableId = 3L;

    CustomerOrder existingOrder =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "key-orig",
            tableId,
            "DINE_IN",
            "Mesa 3",
            new BigDecimal("20.00"),
            "PENDING",
            Set.of(new OrderItem(101L, 1, new BigDecimal("20.00"), "Sin cebolla")));

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(existingOrder));

    AddOrderItemsRequest request =
        new AddOrderItemsRequest(
            Set.of(
                new AddOrderItemsRequest.OrderItemPayload(101L, 1, "Uno mas"),
                new AddOrderItemsRequest.OrderItemPayload(102L, 2, "Bien helada")),
            "Ronda 2");

    when(catalogPriceProvider.getAvailablePrices(branchId, Set.of(101L, 102L)))
        .thenReturn(Map.of(101L, new BigDecimal("20.00"), 102L, new BigDecimal("15.00")));

    RestaurantTableView tableView =
        new RestaurantTableView(tableId, branchId, 1L, "Mesa 3", "Salon", TableState.OCCUPIED);
    when(restaurantTableProvider.findTableByIdAndBranch(tableId, branchId))
        .thenReturn(Optional.of(tableView));

    orderService.addItemsToOrder(branchId, orderId, request);

    // Stock deducted for newly added items
    verify(stockService).deductStock(branchId, 101L, 1);
    verify(stockService).deductStock(branchId, 102L, 2);

    // Order saved with updated total: 20.00 + 20.00 + (15.00 * 2) = 70.00
    ArgumentCaptor<CustomerOrder> orderCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
    verify(orderRepository).save(orderCaptor.capture());
    CustomerOrder savedOrder = orderCaptor.getValue();
    assertThat(savedOrder.totalAmount()).isEqualByComparingTo("70.00");
    assertThat(savedOrder.notes()).contains("[ADICION] Ronda 2");

    // Event published with added items and added SKU IDs only
    ArgumentCaptor<OrderItemsAddedEvent> eventCaptor = ArgumentCaptor.forClass(OrderItemsAddedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    OrderItemsAddedEvent published = eventCaptor.getValue();
    assertThat(published.orderId()).isEqualTo(orderId);
    assertThat(published.tableName()).isEqualTo("Mesa 3");
    assertThat(published.additionNotes()).isEqualTo("Ronda 2");
    assertThat(published.addedSkuIds()).containsExactlyInAnyOrder(101L, 102L);
  }

  @Test
  @DisplayName("Should throw IllegalStateException when trying to add items to an order that is not PENDING")
  void shouldThrowWhenAddingItemsToNonPendingOrder() {
    Long branchId = 1L;
    Long orderId = 10L;

    CustomerOrder paidOrder =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "key-paid",
            null,
            "DINE_IN",
            null,
            new BigDecimal("50.00"),
            "PAID",
            Set.of());

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(paidOrder));

    AddOrderItemsRequest request =
        new AddOrderItemsRequest(
            Set.of(new AddOrderItemsRequest.OrderItemPayload(101L, 1, null)), null);

    assertThatThrownBy(() -> orderService.addItemsToOrder(branchId, orderId, request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No se pueden agregar items a una orden con estado PAID");
  }

  @Test
  @DisplayName("Should cancel order, revert inventory stock, release table, and publish OrderCancelledEvent")
  void shouldCancelOrderSuccessfully() {
    Long branchId = 2L;
    Long orderId = 15L;
    Long tableId = 8L;
    UUID cancelledBy = UUID.randomUUID();
    String reason = "Cliente tuvo una emergencia y se retiro";

    CustomerOrder order =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-cancel",
            tableId,
            "DINE_IN",
            "Mesa 4",
            new BigDecimal("60.00"),
            "PENDING",
            Set.of(
                new OrderItem(101L, 2, new BigDecimal("20.00")),
                new OrderItem(102L, 1, new BigDecimal("20.00"))));

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(order));

    orderService.cancelOrder(branchId, orderId, cancelledBy, reason);

    verify(orderRepository).cancelOrder(orderId, branchId, cancelledBy, reason);
    verify(stockService).addStock(branchId, 101L, 2);
    verify(stockService).addStock(branchId, 102L, 1);
    verify(restaurantTableProvider).updateTableState(tableId, branchId, TableState.AVAILABLE);

    ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    OrderCancelledEvent event = eventCaptor.getValue();
    assertThat(event.orderId()).isEqualTo(orderId);
    assertThat(event.branchId()).isEqualTo(branchId);
    assertThat(event.cancelledBy()).isEqualTo(cancelledBy);
    assertThat(event.cancellationReason()).isEqualTo(reason);
  }

  @Test
  @DisplayName("Should throw IllegalStateException when trying to cancel an already cancelled order")
  void shouldThrowWhenCancellingAlreadyCancelledOrder() {
    Long branchId = 2L;
    Long orderId = 15L;
    UUID cancelledBy = UUID.randomUUID();

    CustomerOrder order =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-cancel",
            null,
            "DINE_IN",
            null,
            new BigDecimal("30.00"),
            "CANCELLED",
            Set.of());

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.cancelOrder(branchId, orderId, cancelledBy, "error"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already CANCELLED");
  }

  @Test
  @DisplayName("Should throw IllegalStateException when trying to cancel a paid order")
  void shouldThrowWhenCancellingPaidOrder() {
    Long branchId = 2L;
    Long orderId = 15L;
    UUID cancelledBy = UUID.randomUUID();

    CustomerOrder order =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-cancel",
            null,
            "DINE_IN",
            null,
            new BigDecimal("30.00"),
            "PAID",
            Set.of());

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.cancelOrder(branchId, orderId, cancelledBy, "error"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already PAID");
  }

  @Test
  @DisplayName("Should calculate pre-check correctly with subtotal, tax 18%, and suggested tip 10%")
  void shouldGetOrderPreCheckCorrectly() {
    Long branchId = 3L;
    Long orderId = 20L;
    Long tableId = 5L;

    CustomerOrder order =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-precheck",
            tableId,
            "DINE_IN",
            "Mesa Principal",
            new BigDecimal("118.00"),
            "PENDING",
            Set.of(new OrderItem(201L, 2, new BigDecimal("59.00"))));

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(order));
    when(catalogPriceProvider.getSkuNames(Set.of(201L)))
        .thenReturn(Map.of(201L, "Lomo Saltado Familiar"));

    RestaurantTableView tableView =
        new RestaurantTableView(tableId, branchId, 1L, "Mesa 5", "Terraza", TableState.OCCUPIED);
    when(restaurantTableProvider.findTableByIdAndBranch(tableId, branchId))
        .thenReturn(Optional.of(tableView));

    PreCheckResponse preCheck = orderService.getOrderPreCheck(branchId, orderId);

    assertThat(preCheck.orderId()).isEqualTo(orderId);
    assertThat(preCheck.tableName()).isEqualTo("Mesa 5");
    assertThat(preCheck.totalAmount()).isEqualByComparingTo("118.00");
    // 118.00 / 1.18 = 100.00
    assertThat(preCheck.subtotal()).isEqualByComparingTo("100.00");
    // 118.00 - 100.00 = 18.00
    assertThat(preCheck.taxAmount()).isEqualByComparingTo("18.00");
    // 118.00 * 0.10 = 11.80
    assertThat(preCheck.suggestedTip10()).isEqualByComparingTo("11.80");
    assertThat(preCheck.items()).hasSize(1);
    assertThat(preCheck.items().get(0).name()).isEqualTo("Lomo Saltado Familiar");
  }

  @Test
  @DisplayName("Should publish PreCheckRequestedEvent when print is requested")
  void shouldPublishPreCheckRequestedEvent() {
    Long branchId = 3L;
    Long orderId = 20L;
    Long targetPrinterId = 99L;

    CustomerOrder order =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-precheck",
            null,
            "TAKE_OUT",
            null,
            new BigDecimal("50.00"),
            "PENDING",
            Set.of());

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(order));

    orderService.requestPreCheckPrint(branchId, orderId, targetPrinterId);

    ArgumentCaptor<PreCheckRequestedEvent> captor = ArgumentCaptor.forClass(PreCheckRequestedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    PreCheckRequestedEvent event = captor.getValue();
    assertThat(event.orderId()).isEqualTo(orderId);
    assertThat(event.branchId()).isEqualTo(branchId);
    assertThat(event.targetPrinterId()).isEqualTo(targetPrinterId);
  }

  @Test
  @DisplayName("Should update order status to DELIVERED and publish OrderStatusChangedEvent")
  void shouldUpdateOrderStatusToDelivered() {
    Long branchId = 3L;
    Long orderId = 20L;
    CustomerOrder order =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-status",
            null,
            "DINE_IN",
            null,
            new BigDecimal("50.00"),
            "PENDING",
            Set.of());

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(order));

    orderService.updateOrderStatus(branchId, orderId, "DELIVERED");

    verify(orderRepository).updateStatus(orderId, branchId, "DELIVERED");
    ArgumentCaptor<OrderStatusChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    OrderStatusChangedEvent event = eventCaptor.getValue();
    assertThat(event.orderId()).isEqualTo(orderId);
    assertThat(event.branchId()).isEqualTo(branchId);
    assertThat(event.previousStatus()).isEqualTo("PENDING");
    assertThat(event.newStatus()).isEqualTo("DELIVERED");
  }

  @Test
  @DisplayName("Should mark order as delivered via markOrderAsDelivered")
  void shouldMarkOrderAsDelivered() {
    Long branchId = 3L;
    Long orderId = 21L;
    CustomerOrder order =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-deliv",
            null,
            "DINE_IN",
            null,
            new BigDecimal("30.00"),
            "PENDING",
            Set.of());

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(order));

    orderService.markOrderAsDelivered(branchId, orderId);

    verify(orderRepository).updateStatus(orderId, branchId, "DELIVERED");
  }

  @Test
  @DisplayName("Should throw IllegalStateException when updating status of already PAID or CANCELLED order")
  void shouldThrowWhenUpdatingPaidOrCancelledOrder() {
    Long branchId = 3L;
    Long orderId = 22L;
    CustomerOrder paidOrder =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-paid",
            null,
            "DINE_IN",
            null,
            new BigDecimal("30.00"),
            "PAID",
            Set.of());

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(paidOrder));

    assertThatThrownBy(() -> orderService.updateOrderStatus(branchId, orderId, "DELIVERED"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PAGADA");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when passing invalid status")
  void shouldThrowWhenPassingInvalidStatus() {
    assertThatThrownBy(() -> orderService.updateOrderStatus(1L, 1L, "INVALID_STATUS"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Estado no válido");

    assertThatThrownBy(() -> orderService.updateOrderStatus(1L, 1L, "PAID"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cobro");

    assertThatThrownBy(() -> orderService.updateOrderStatus(1L, 1L, "CANCELLED"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cancelación");
  }

  @Test
  @DisplayName("Should map createdAt, updatedAt, and isModified properly in OrderResponse")
  void shouldMapAuditTimestampsAndIsModified() {
    Long branchId = 3L;
    Long orderId = 23L;
    java.time.OffsetDateTime created = java.time.OffsetDateTime.now().minusMinutes(10);
    java.time.OffsetDateTime updated = java.time.OffsetDateTime.now();

    CustomerOrder modifiedOrder =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-mod",
            null,
            "DINE_IN",
            "Mesa 1 | [ADICION] Bebidas",
            new BigDecimal("45.00"),
            "PENDING",
            null,
            null,
            null,
            created,
            updated,
            Set.of());

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(modifiedOrder));

    OrderResponse response = orderService.getEnrichedOrderByIdAndBranch(orderId, branchId);

    assertThat(response.createdAt()).isEqualTo(created);
    assertThat(response.updatedAt()).isEqualTo(updated);
    assertThat(response.isModified()).isTrue();
  }

  @Test
  @DisplayName("Should place order without deducting stock for SKUs with requiresStock = false")
  void shouldPlaceOrderWithoutDeductingStockWhenSkuDoesNotRequireStock() {
    Long branchId = 5L;
    UUID userId = UUID.randomUUID();
    String idempotencyKey = UUID.randomUUID().toString();
    String email = "waiter@facilcomanda.com";

    // SKU 101: Lomo Saltado (requiresStock = false)
    // SKU 102: Gaseosa 500ml (requiresStock = true)
    PlaceOrderRequest request =
        new PlaceOrderRequest(
            Set.of(
                new PlaceOrderRequest.OrderItemPayload(101L, 2),
                new PlaceOrderRequest.OrderItemPayload(102L, 3)));

    when(catalogPriceProvider.getAvailablePrices(branchId, Set.of(101L, 102L)))
        .thenReturn(
            Map.of(
                101L, new BigDecimal("35.00"),
                102L, new BigDecimal("8.00")));
    when(catalogPriceProvider.getRequiresStockForSkus(Set.of(101L, 102L)))
        .thenReturn(Map.of(101L, false, 102L, true));

    CustomerOrder savedOrder =
        new CustomerOrder(
            50L,
            branchId,
            userId,
            idempotencyKey,
            new BigDecimal("94.00"),
            "PENDING",
            Set.of(
                new OrderItem(101L, 2, new BigDecimal("35.00")),
                new OrderItem(102L, 3, new BigDecimal("8.00"))));

    when(orderRepository.save(any(CustomerOrder.class))).thenReturn(savedOrder);

    Long orderId = orderService.placeOrder(branchId, userId, idempotencyKey, email, request);

    assertThat(orderId).isEqualTo(50L);
    // Stock must NOT be deducted for non-stockable dish (SKU 101)
    org.mockito.Mockito.verify(stockService, org.mockito.Mockito.never())
        .deductStock(branchId, 101L, 2);
    // Stock MUST be deducted for stockable beverage (SKU 102)
    verify(stockService).deductStock(branchId, 102L, 3);
  }

  @Test
  @DisplayName("Should cancel order and only replenish stock for SKUs with requiresStock = true")
  void shouldCancelOrderAndSkipRestockForNonStockableSkus() {
    Long branchId = 2L;
    Long orderId = 15L;
    UUID cancelledBy = UUID.randomUUID();

    CustomerOrder order =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-cancel-nonstock",
            null,
            "DINE_IN",
            null,
            new BigDecimal("55.00"),
            "PENDING",
            Set.of(
                new OrderItem(101L, 1, new BigDecimal("35.00")),
                new OrderItem(102L, 2, new BigDecimal("10.00"))));

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(order));
    when(catalogPriceProvider.getRequiresStockForSkus(Set.of(101L, 102L)))
        .thenReturn(Map.of(101L, false, 102L, true));

    orderService.cancelOrder(branchId, orderId, cancelledBy, "Cancelado por cliente");

    org.mockito.Mockito.verify(stockService, org.mockito.Mockito.never())
        .addStock(branchId, 101L, 1);
    verify(stockService).addStock(branchId, 102L, 2);
  }

  @Test
  @DisplayName("Rule 1 & 2: Should allow creating a second order on an OCCUPIED table when >= 10s have elapsed since last PENDING order")
  void shouldAllowMultipleOpenOrdersOnOccupiedTableAfter10Seconds() {
    Long branchId = 2L;
    Long tableId = 15L;
    UUID userId = UUID.randomUUID();
    String idempotencyKey = "order-second-round";

    PlaceOrderRequest request =
        new PlaceOrderRequest(
            tableId,
            "DINE_IN",
            "Segunda ronda",
            Set.of(new PlaceOrderRequest.OrderItemPayload(201L, 2, "Heladas")));

    RestaurantTableView occupiedTable =
        new RestaurantTableView(tableId, branchId, 3L, "Mesa 4", "Terraza", TableState.OCCUPIED);
    when(restaurantTableProvider.lockTableByIdAndBranch(tableId, branchId))
        .thenReturn(Optional.of(occupiedTable));
    when(orderRepository.findLatestPendingOrderCreatedAtByTable(branchId, tableId))
        .thenReturn(Optional.of(java.time.OffsetDateTime.now().minusSeconds(12)));

    when(catalogPriceProvider.getAvailablePrices(branchId, Set.of(201L)))
        .thenReturn(Map.of(201L, new BigDecimal("15.00")));

    CustomerOrder savedOrder =
        new CustomerOrder(
            100L,
            branchId,
            userId,
            idempotencyKey,
            tableId,
            "DINE_IN",
            "Segunda ronda",
            new BigDecimal("30.00"),
            "PENDING",
            Set.of(new OrderItem(201L, 2, new BigDecimal("15.00"), "Heladas", "PENDING")));
    when(orderRepository.save(any(CustomerOrder.class))).thenReturn(savedOrder);

    Long orderId =
        orderService.placeOrder(branchId, userId, idempotencyKey, "waiter@facilcomanda.com", request);

    assertThat(orderId).isEqualTo(100L);
    org.mockito.Mockito.verify(restaurantTableProvider, org.mockito.Mockito.never())
        .occupyTableAtomically(tableId, branchId);
  }

  @Test
  @DisplayName("Rule 2: Should reject new order on table when less than 10 seconds have elapsed since last PENDING order")
  void shouldRejectOrderWhenLessThan10SecondsSinceLastPendingOrderOnTable() {
    Long branchId = 2L;
    Long tableId = 15L;
    PlaceOrderRequest request =
        new PlaceOrderRequest(
            tableId,
            "DINE_IN",
            "Orden muy rápida",
            Set.of(new PlaceOrderRequest.OrderItemPayload(201L, 1)));

    RestaurantTableView occupiedTable =
        new RestaurantTableView(tableId, branchId, 3L, "Mesa 4", "Terraza", TableState.OCCUPIED);
    when(restaurantTableProvider.lockTableByIdAndBranch(tableId, branchId))
        .thenReturn(Optional.of(occupiedTable));
    when(orderRepository.findLatestPendingOrderCreatedAtByTable(branchId, tableId))
        .thenReturn(Optional.of(java.time.OffsetDateTime.now().minusSeconds(4)));

    assertThatThrownBy(
            () ->
                orderService.placeOrder(
                    branchId,
                    UUID.randomUUID(),
                    "idemp-too-fast",
                    "waiter@facilcomanda.com",
                    request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Debe esperar al menos 10 segundos");
  }

  @Test
  @DisplayName("Idempotency: Should return existing order ID without re-deducting stock on retry with same Idempotency-Key")
  void shouldReturnExistingOrderIdWhenIdempotencyKeyAlreadyExists() {
    Long branchId = 2L;
    UUID userId = UUID.randomUUID();
    String idempotencyKey = "retry-key-123";

    CustomerOrder existingOrder =
        new CustomerOrder(
            77L,
            branchId,
            userId,
            idempotencyKey,
            new BigDecimal("40.00"),
            "PENDING",
            Set.of(new OrderItem(101L, 2, new BigDecimal("20.00"))));

    when(orderRepository.findByBranchIdAndIdempotencyKey(branchId, idempotencyKey))
        .thenReturn(Optional.of(existingOrder));

    PlaceOrderRequest request =
        new PlaceOrderRequest(Set.of(new PlaceOrderRequest.OrderItemPayload(101L, 2)));

    Long orderId =
        orderService.placeOrder(branchId, userId, idempotencyKey, "waiter@facilcomanda.com", request);

    assertThat(orderId).isEqualTo(77L);
    org.mockito.Mockito.verify(stockService, org.mockito.Mockito.never())
        .deductStock(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  @DisplayName("Rule 3 & 4: Order transitions to COMPLETED only when ALL active items are COMPLETED")
  void shouldTransitionOrderToCompletedOnlyWhenAllActiveItemsAreCompleted() {
    Long branchId = 1L;
    Long orderId = 50L;

    CustomerOrder orderWithTwoPendingItems =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "key-items",
            5L,
            "DINE_IN",
            null,
            new BigDecimal("50.00"),
            "PENDING",
            Set.of(
                new OrderItem(101L, 1, new BigDecimal("30.00"), null, "PENDING"),
                new OrderItem(102L, 2, new BigDecimal("10.00"), null, "PENDING")));

    when(orderRepository.findByIdAndBranchId(orderId, branchId))
        .thenReturn(Optional.of(orderWithTwoPendingItems));

    // Step 1: Attend first item (101L) -> Order must remain PENDING because 102L is still PENDING
    orderService.attendOrderItem(branchId, orderId, 101L);

    ArgumentCaptor<CustomerOrder> firstSaveCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
    verify(orderRepository).save(firstSaveCaptor.capture());
    CustomerOrder afterFirstItem = firstSaveCaptor.getValue();
    assertThat(afterFirstItem.status()).isEqualTo("PENDING");

    // Step 2: Attend second item (102L) -> All active items are now COMPLETED -> Order becomes COMPLETED
    when(orderRepository.findByIdAndBranchId(orderId, branchId))
        .thenReturn(Optional.of(afterFirstItem));

    orderService.attendOrderItem(branchId, orderId, 102L);

    ArgumentCaptor<CustomerOrder> secondSaveCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
    org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.times(2))
        .save(secondSaveCaptor.capture());
    CustomerOrder afterSecondItem = secondSaveCaptor.getAllValues().get(1);
    assertThat(afterSecondItem.status()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("Rule 3 & 4: Editing an item in a COMPLETED order sets item to MODIFIED and transitions order back to PENDING")
  void shouldSetItemToModifiedAndOrderBackToPendingWhenItemIsEdited() {
    Long branchId = 1L;
    Long orderId = 60L;

    CustomerOrder completedOrder =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "key-comp",
            5L,
            "DINE_IN",
            null,
            new BigDecimal("30.00"),
            "COMPLETED",
            Set.of(new OrderItem(101L, 1, new BigDecimal("30.00"), "Normal", "COMPLETED")));

    when(orderRepository.findByIdAndBranchId(orderId, branchId))
        .thenReturn(Optional.of(completedOrder));
    when(catalogPriceProvider.getRequiresStockForSkus(Set.of(101L)))
        .thenReturn(Map.of(101L, true));

    // Edit item 101L: increase quantity from 1 to 3 and update comments
    orderService.updateOrderItem(
        branchId, orderId, 101L, new UpdateOrderItemRequest(3, "Con papas extra"));

    // Delta = 3 - 1 = 2 deducted from stock
    verify(stockService).deductStock(branchId, 101L, 2);

    ArgumentCaptor<CustomerOrder> orderCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
    verify(orderRepository).save(orderCaptor.capture());
    CustomerOrder updatedOrder = orderCaptor.getValue();

    // Order returns to PENDING and total is updated to 90.00
    assertThat(updatedOrder.status()).isEqualTo("PENDING");
    assertThat(updatedOrder.totalAmount()).isEqualByComparingTo("90.00");
    OrderItem editedItem = updatedOrder.items().iterator().next();
    assertThat(editedItem.status()).isEqualTo("MODIFIED");
    assertThat(editedItem.quantity()).isEqualTo(3);
    assertThat(editedItem.comments()).isEqualTo("Con papas extra");
  }

  @Test
  @DisplayName("Rule 5: Should NOT release table to AVAILABLE when cancelling or paying one order if other active orders remain on the table")
  void shouldKeepTableOccupiedWhenOtherActiveOrdersRemainOnTable() {
    Long branchId = 2L;
    Long orderId = 15L;
    Long tableId = 8L;
    UUID cancelledBy = UUID.randomUUID();

    CustomerOrder order =
        new CustomerOrder(
            orderId,
            branchId,
            UUID.randomUUID(),
            "idemp-multi-1",
            tableId,
            "DINE_IN",
            null,
            new BigDecimal("20.00"),
            "PENDING",
            Set.of(new OrderItem(101L, 1, new BigDecimal("20.00"))));

    when(orderRepository.findByIdAndBranchId(orderId, branchId)).thenReturn(Optional.of(order));
    // Simulate 1 remaining active order on table 8
    when(orderRepository.countActiveOrdersByTableId(branchId, tableId)).thenReturn(1L);

    orderService.cancelOrder(branchId, orderId, cancelledBy, "Cancela primera orden");

    // Table must NOT be released to AVAILABLE while another active order exists on table 8
    org.mockito.Mockito.verify(restaurantTableProvider, org.mockito.Mockito.never())
        .updateTableState(tableId, branchId, TableState.AVAILABLE);

    // Now when the last active order is paid (countActiveOrdersByTableId returns 0), table IS released
    when(orderRepository.countActiveOrdersByTableId(branchId, tableId)).thenReturn(0L);
    orderService.markOrderAsPaid(branchId, orderId);

    verify(restaurantTableProvider).updateTableState(tableId, branchId, TableState.AVAILABLE);
  }
}
