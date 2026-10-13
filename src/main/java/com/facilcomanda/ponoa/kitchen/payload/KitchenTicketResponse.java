package com.facilcomanda.ponoa.kitchen.payload;

import com.facilcomanda.ponoa.kitchen.KitchenTicketStatus;
import java.time.OffsetDateTime;

public record KitchenTicketResponse(
    Long id,
    Long branchId,
    Long orderId,
    KitchenTicketStatus status,
    String notes,
    OffsetDateTime createdAt) {}
