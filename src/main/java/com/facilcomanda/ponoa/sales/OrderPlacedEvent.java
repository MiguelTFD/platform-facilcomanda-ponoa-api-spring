package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import java.util.Set;

public record OrderPlacedEvent(
    Long orderId,
    Long branchId,
    String customerEmail,
    BigDecimal totalAmount,
    Set<Long> skuIds) {

  public OrderPlacedEvent(
      Long orderId,
      Long branchId,
      String customerEmail,
      BigDecimal totalAmount) {
    this(orderId, branchId, customerEmail, totalAmount, Set.of());
  }
}
