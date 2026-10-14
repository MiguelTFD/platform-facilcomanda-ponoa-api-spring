package com.facilcomanda.ponoa.cash.payload;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record OpenCashShiftRequest(
    @NotNull(message = "cashRegisterId is required") Long cashRegisterId,
    @NotNull(message = "openingAmount is required") @PositiveOrZero BigDecimal openingAmount,
    String notes) {}
