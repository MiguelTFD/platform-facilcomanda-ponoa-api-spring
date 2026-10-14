package com.facilcomanda.ponoa.cash.payload;

import jakarta.validation.constraints.NotBlank;

public record CreateCashRegisterRequest(
    @NotBlank(message = "Cash register name is required") String name,
    String description) {}
