package com.facilcomanda.ponoa.kitchen.payload;

import com.facilcomanda.ponoa.kitchen.KitchenTicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest(@NotNull KitchenTicketStatus status) {}
