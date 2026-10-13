package com.facilcomanda.ponoa.printing.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PrintCashierReceiptRequest(
    @NotNull Long orderId,
    @NotBlank String terminalCode,
    String idempotencyKey) {}
