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
}
