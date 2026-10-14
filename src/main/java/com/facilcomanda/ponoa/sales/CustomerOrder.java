package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("customer_orders")
public record CustomerOrder(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("app_user_id") UUID appUserId,
    @Column("idempotency_key") String idempotencyKey,
    @Column("table_id") Long tableId,
    @Column("order_type") String orderType,
    String notes,
    BigDecimal totalAmount,
    String status,
    @Column("cancelled_by") UUID cancelledBy,
    @Column("cancellation_reason") String cancellationReason,
    @Column("cancelled_at") OffsetDateTime cancelledAt,
    @MappedCollection(idColumn = "order_id") Set<OrderItem> items) {

  public CustomerOrder(
      Long id,
      Long branchId,
      UUID appUserId,
      String idempotencyKey,
      Long tableId,
      String orderType,
      String notes,
      BigDecimal totalAmount,
      String status,
      Set<OrderItem> items) {
    this(
        id,
        branchId,
        appUserId,
        idempotencyKey,
        tableId,
        orderType,
        notes,
        totalAmount,
        status,
        null,
        null,
        null,
        items);
  }

  public CustomerOrder(
      Long id,
      Long branchId,
      UUID appUserId,
      String idempotencyKey,
      BigDecimal totalAmount,
      String status,
      Set<OrderItem> items) {
    this(id, branchId, appUserId, idempotencyKey, null, "DINE_IN", null, totalAmount, status, items);
  }
}
