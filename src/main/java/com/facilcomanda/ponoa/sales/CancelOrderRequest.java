package com.facilcomanda.ponoa.sales;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderRequest(
    @NotBlank(message = "Cancellation reason is required") String reason) {}
