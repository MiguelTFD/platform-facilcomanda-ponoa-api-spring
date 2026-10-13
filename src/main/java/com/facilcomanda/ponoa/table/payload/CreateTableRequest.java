package com.facilcomanda.ponoa.table.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTableRequest(
    @NotNull Long floorId,
    @NotBlank String name,
    String description,
    @Positive Integer chairs) {}
