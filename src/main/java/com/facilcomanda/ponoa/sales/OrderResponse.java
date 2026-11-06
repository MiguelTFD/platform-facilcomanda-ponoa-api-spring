package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    Long id,
    Long branchId,
    UUID appUserId,
    String idempotencyKey,
    Long tableId,
    String tableName,
    String orderType,
    String notes,
    BigDecimal totalAmount,
    String status,
    UUID cancelledBy,
    String cancellationReason,
    OffsetDateTime cancelledAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    Boolean isModified,
    List<OrderItemResponse> items) {

  public OrderResponse(
      Long id,
      Long branchId,
      UUID appUserId,
      String idempotencyKey,
      Long tableId,
      String tableName,
      String orderType,
      String notes,
      BigDecimal totalAmount,
      String status,
      UUID cancelledBy,
      String cancellationReason,
      OffsetDateTime cancelledAt,
      List<OrderItemResponse> items) {
    this(
        id,
        branchId,
        appUserId,
        idempotencyKey,
        tableId,
        tableName,
        orderType,
        notes,
        totalAmount,
        status,
        cancelledBy,
        cancellationReason,
        cancelledAt,
        null,
        null,
        false,
        items);
  }

  public record OrderItemResponse(
      Long skuId,
      String name,
      Integer quantity,
      BigDecimal unitPrice,
      BigDecimal subtotal,
      String comments,
      String status) {

    public OrderItemResponse(
        Long skuId,
        String name,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        String comments) {
      this(skuId, name, quantity, unitPrice, subtotal, comments, "PENDING");
    }
  }
}
