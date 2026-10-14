package com.facilcomanda.ponoa.sales;

import java.util.UUID;

public record OrderCancelledEvent(
    Long orderId,
    Long branchId,
    String cancellationReason,
    UUID cancelledBy) {}
