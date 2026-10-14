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
    List<OrderItemResponse> items) {

  public record OrderItemResponse(
      Long skuId,
      String name,
      Integer quantity,
      BigDecimal unitPrice,
      BigDecimal subtotal,
      String comments) {}
}
