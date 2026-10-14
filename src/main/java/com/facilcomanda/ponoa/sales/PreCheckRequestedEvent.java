package com.facilcomanda.ponoa.sales;

public record PreCheckRequestedEvent(
    Long orderId,
    Long branchId,
    Long targetPrinterId) {}
