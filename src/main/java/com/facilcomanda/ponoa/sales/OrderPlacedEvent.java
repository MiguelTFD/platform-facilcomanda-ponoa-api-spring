package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import java.util.Set;

public record OrderPlacedEvent(
    Long orderId,
    Long branchId,
    String customerEmail,
    BigDecimal totalAmount,
    Set<Long> skuIds,
    Long tableId,
    String tableName,
    String orderType,
    String notes) {

  public OrderPlacedEvent(
      Long orderId,
      Long branchId,
      String customerEmail,
      BigDecimal totalAmount,
      Set<Long> skuIds) {
    this(orderId, branchId, customerEmail, totalAmount, skuIds, null, null, "DINE_IN", null);
  }

  public OrderPlacedEvent(
      Long orderId,
      Long branchId,
      String customerEmail,
      BigDecimal totalAmount) {
    this(orderId, branchId, customerEmail, totalAmount, Set.of(), null, null, "DINE_IN", null);
  }
}
