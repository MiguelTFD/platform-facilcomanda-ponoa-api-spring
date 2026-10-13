package com.facilcomanda.ponoa.printing.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RegisterPrinterRequest(
    @NotBlank String name,
    @NotBlank String ipAddress,
    @NotNull @Positive Integer port,
    String connectionType) {}
