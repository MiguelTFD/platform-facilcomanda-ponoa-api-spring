package com.facilcomanda.ponoa.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.catalog.CatalogPriceProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.inventory.StockService;
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
}
