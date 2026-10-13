package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;

public record OrderPlacedEvent(
    Long orderId,
    Long branchId,
    String customerEmail,
    BigDecimal totalAmount) {}
