package com.facilcomanda.ponoa.sales;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateOrderItemRequest(
    @NotNull(message = "La cantidad del ítem es obligatoria")
    @Positive(message = "La cantidad del ítem debe ser mayor a 0")
    Integer quantity,
    String comments) {}
