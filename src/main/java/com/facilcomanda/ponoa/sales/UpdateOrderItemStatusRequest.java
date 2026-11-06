package com.facilcomanda.ponoa.sales;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrderItemStatusRequest(
    @NotBlank(message = "El estado del ítem es obligatorio (PENDING, COMPLETED, MODIFIED, CANCELLED)")
    String status) {}
