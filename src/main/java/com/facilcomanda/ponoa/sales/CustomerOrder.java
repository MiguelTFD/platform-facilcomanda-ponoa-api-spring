package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
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
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt,
    @MappedCollection(idColumn = "order_id") Set<OrderItem> items) {

  @PersistenceCreator
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
      UUID cancelledBy,
      String cancellationReason,
      OffsetDateTime cancelledAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      Set<OrderItem> items) {
    this.id = id;
    this.branchId = branchId;
    this.appUserId = appUserId;
    this.idempotencyKey = idempotencyKey;
    this.tableId = tableId;
    this.orderType = orderType;
    this.notes = notes;
    this.totalAmount = totalAmount;
    this.status = status;
    this.cancelledBy = cancelledBy;
    this.cancellationReason = cancellationReason;
    this.cancelledAt = cancelledAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.items = items;
  }

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
      UUID cancelledBy,
      String cancellationReason,
      OffsetDateTime cancelledAt,
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
        cancelledBy,
        cancellationReason,
        cancelledAt,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        items);
  }

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
        OffsetDateTime.now(),
        OffsetDateTime.now(),
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
