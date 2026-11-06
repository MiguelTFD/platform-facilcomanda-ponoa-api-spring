package com.facilcomanda.ponoa.sales;

public record OrderStatusChangedEvent(
    Long orderId,
    Long branchId,
    String previousStatus,
    String newStatus) {}
