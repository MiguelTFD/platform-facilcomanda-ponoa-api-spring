package com.facilcomanda.ponoa.printing.payload;

import jakarta.validation.constraints.NotNull;

public record PrintPreCheckRequest(
    @NotNull Long orderId,
    Long targetPrinterId) {}
