package com.facilcomanda.ponoa.printing.payload;

import jakarta.validation.constraints.NotNull;

public record ReprintReceiptRequest(
    @NotNull Long orderId,
    String terminalCode,
    Long targetPrinterId,
    String reason) {}
