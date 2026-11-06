package com.facilcomanda.ponoa.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.facilcomanda.ponoa.TestcontainersConfiguration;
import com.facilcomanda.ponoa.table.RestaurantTableProvider;
import com.facilcomanda.ponoa.table.RestaurantTableView;
import com.facilcomanda.ponoa.table.TableState;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MultiOrderAndItemStatusIntegrationTest {

  @Autowired private OrderService orderService;
  @Autowired private RestaurantTableProvider restaurantTableProvider;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long branchId;
  private UUID userId;
  private Long tableId;
  private Long skuLomoId;
  private Long skuChichaId;

  @BeforeEach
  void setUpTestData() {
    userId = UUID.randomUUID();
    String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

    jdbcTemplate.update(
        "INSERT INTO app_users (id, email, first_name, last_name, role) VALUES (?, ?, 'Mesero', 'Test', 'MESERO')",
        userId,
        "mesero-" + uniqueSuffix + "@test.com");

    branchId =
        jdbcTemplate.queryForObject(
            "INSERT INTO branches (name, code, address) VALUES (?, ?, 'Av. Principal 123') RETURNING id",
            Long.class,
            "Sucursal Test " + uniqueSuffix,
            "BR-" + uniqueSuffix);

    Long floorId =
        jdbcTemplate.queryForObject(
            "INSERT INTO restaurant_floors (branch_id, name, description) VALUES (?, 'Piso 1', 'Primer piso') RETURNING id",
            Long.class,
            branchId);

    Long zoneId =
        jdbcTemplate.queryForObject(
            "INSERT INTO restaurant_zones (branch_id, floor_id, name, description) VALUES (?, ?, 'Zona Karaoke', 'Área VIP') RETURNING id",
            Long.class,
            branchId,
            floorId);

    tableId =
        jdbcTemplate.queryForObject(
            "INSERT INTO restaurant_tables (branch_id, floor_id, zone_id, name, state, chairs) VALUES (?, ?, ?, 'Mesa VIP 1', 'AVAILABLE', 6) RETURNING id",
            Long.class,
            branchId,
            floorId,
            zoneId);

    Long productId =
        jdbcTemplate.queryForObject(
            "INSERT INTO products (name, production_area) VALUES (?, 'COCINA') RETURNING id",
            Long.class,
            "Lomo Saltado " + uniqueSuffix);

    skuLomoId =
        jdbcTemplate.queryForObject(
            "INSERT INTO sku_presentations (product_id, sku_code, presentation_name, current_price, requires_stock) VALUES (?, ?, 'Plato Personal', 35.00, FALSE) RETURNING id",
            Long.class,
            productId,
            "SKU-LOMO-" + uniqueSuffix);

    skuChichaId =
        jdbcTemplate.queryForObject(
            "INSERT INTO sku_presentations (product_id, sku_code, presentation_name, current_price, requires_stock) VALUES (?, ?, 'Jarra Chicha 1L', 18.00, FALSE) RETURNING id",
            Long.class,
            productId,
            "SKU-CHICHA-" + uniqueSuffix);
  }

  @Test
  @DisplayName(
      "Full integration flow: multi-order table, 10s cooldown on PENDING order, idempotency, item COMPLETED/MODIFIED/CANCELLED, and table release only when all orders close")
  void shouldExecuteMultiOrderAndItemLifecycleEndToEnd() {
    String idempOrder1 = UUID.randomUUID().toString();
    PlaceOrderRequest req1 =
        new PlaceOrderRequest(
            tableId,
            "DINE_IN",
            "Primera ronda",
            Set.of(
                new PlaceOrderRequest.OrderItemPayload(skuLomoId, 1, "Término medio"),
                new PlaceOrderRequest.OrderItemPayload(skuChichaId, 1, "Helada")));

    // 1. Place Order #1 on AVAILABLE table -> table becomes OCCUPIED
    Long order1Id =
        orderService.placeOrder(branchId, userId, idempOrder1, "mesero@test.com", req1);
    assertThat(order1Id).isNotNull();

    RestaurantTableView tableAfterOrder1 =
        restaurantTableProvider.findTableByIdAndBranch(tableId, branchId).orElseThrow();
    assertThat(tableAfterOrder1.state()).isEqualTo(TableState.OCCUPIED);

    // 2. Idempotent retry with the same Idempotency-Key returns the exact same order1Id
    Long retryOrder1Id =
        orderService.placeOrder(branchId, userId, idempOrder1, "mesero@test.com", req1);
    assertThat(retryOrder1Id).isEqualTo(order1Id);

    // 3. Attempting to place Order #2 on the same table while Order #1 is PENDING and < 10s old MUST fail
    PlaceOrderRequest req2 =
        new PlaceOrderRequest(
            tableId,
            "DINE_IN",
            "Segunda ronda",
            Set.of(new PlaceOrderRequest.OrderItemPayload(skuChichaId, 2, "Sin hielo")));

    assertThatThrownBy(
            () ->
                orderService.placeOrder(
                    branchId,
                    userId,
                    "idemp-order-2-fast-" + UUID.randomUUID(),
                    "mesero@test.com",
                    req2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Debe esperar al menos 10 segundos");

    // 4. Attend first item of Order #1 -> Order #1 stays PENDING because second item is still PENDING
    orderService.attendOrderItem(branchId, order1Id, skuLomoId);
    OrderResponse order1AfterFirstAttend =
        orderService.getEnrichedOrderByIdAndBranch(order1Id, branchId);
    assertThat(order1AfterFirstAttend.status()).isEqualTo("PENDING");

    // 5. Attend second item of Order #1 -> All active items are COMPLETED -> Order #1 becomes COMPLETED!
    orderService.attendOrderItem(branchId, order1Id, skuChichaId);
    OrderResponse order1AfterAllAttended =
        orderService.getEnrichedOrderByIdAndBranch(order1Id, branchId);
    assertThat(order1AfterAllAttended.status()).isEqualTo("COMPLETED");
    assertThat(order1AfterAllAttended.items())
        .allMatch(item -> "COMPLETED".equals(item.status()));

    // 6. Now that Order #1 is COMPLETED (no PENDING order on table), Order #2 is created instantaneously!
    Long order2Id =
        orderService.placeOrder(
            branchId,
            userId,
            "idemp-order-2-" + UUID.randomUUID(),
            "mesero@test.com",
            req2);
    assertThat(order2Id).isNotNull().isNotEqualTo(order1Id);

    // Verify table now has 2 active open orders simultaneously
    List<OrderResponse> activeTableOrders =
        orderService.getActiveOrdersByTable(branchId, tableId);
    assertThat(activeTableOrders).hasSize(2);

    // 7. Edit an item in Order #1 -> Item becomes MODIFIED and Order #1 returns to PENDING
    orderService.updateOrderItem(
        branchId, order1Id, skuLomoId, new UpdateOrderItemRequest(2, "Dos porciones"));
    OrderResponse order1AfterEdit =
        orderService.getEnrichedOrderByIdAndBranch(order1Id, branchId);
    assertThat(order1AfterEdit.status()).isEqualTo("PENDING");
    assertThat(order1AfterEdit.isModified()).isTrue();
    assertThat(order1AfterEdit.totalAmount()).isEqualByComparingTo("88.00"); // (35*2) + 18 = 88
    assertThat(
            order1AfterEdit.items().stream()
                .filter(i -> i.skuId().equals(skuLomoId))
                .findFirst()
                .orElseThrow()
                .status())
        .isEqualTo("MODIFIED");

    // 8. Pay Order #1 -> Table MUST remain OCCUPIED because Order #2 is still open
    orderService.markOrderAsPaid(branchId, order1Id);
    RestaurantTableView tableAfterOrder1Paid =
        restaurantTableProvider.findTableByIdAndBranch(tableId, branchId).orElseThrow();
    assertThat(tableAfterOrder1Paid.state()).isEqualTo(TableState.OCCUPIED);

    // 9. Cancel Order #2 -> All orders on table are now PAID or CANCELLED -> Table becomes AVAILABLE
    orderService.cancelOrder(branchId, order2Id, userId, "Cliente canceló segunda ronda");
    RestaurantTableView tableAfterAllClosed =
        restaurantTableProvider.findTableByIdAndBranch(tableId, branchId).orElseThrow();
    assertThat(tableAfterAllClosed.state()).isEqualTo(TableState.AVAILABLE);
  }
}
