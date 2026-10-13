package com.facilcomanda.ponoa.printing.payload;

import jakarta.validation.constraints.NotNull;

public record ReprintKitchenOrderRequest(
    @NotNull Long orderId,
    String productionArea,
    Long targetPrinterId,
    String reason) {}
