package com.facilcomanda.ponoa.cash.payload;

import com.facilcomanda.ponoa.cash.CashMovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AddCashMovementRequest(
    @NotNull(message = "Movement type is required") CashMovementType type,
    @NotNull(message = "Amount is required") @Positive BigDecimal amount,
    @NotBlank(message = "Reason is required") String reason) {}
