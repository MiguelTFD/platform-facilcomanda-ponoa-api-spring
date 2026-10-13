package com.facilcomanda.ponoa.inventory.payload;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InitializeStockRequest(
    @NotNull Long skuId,
    @NotNull @PositiveOrZero Integer quantity) {}
