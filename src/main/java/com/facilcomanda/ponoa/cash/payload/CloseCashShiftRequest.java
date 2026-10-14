package com.facilcomanda.ponoa.cash.payload;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CloseCashShiftRequest(
    @NotNull(message = "countedCash is required") @PositiveOrZero BigDecimal countedCash,
    String notes) {}
