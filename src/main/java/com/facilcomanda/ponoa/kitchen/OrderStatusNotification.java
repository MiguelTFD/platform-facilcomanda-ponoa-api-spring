package com.facilcomanda.ponoa.kitchen;

public record OrderStatusNotification(
    Long orderId,
    Long branchId,
    KitchenTicketStatus status,
    String notes) {}
