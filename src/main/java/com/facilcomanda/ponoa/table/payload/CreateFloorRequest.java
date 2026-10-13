package com.facilcomanda.ponoa.table.payload;

import jakarta.validation.constraints.NotBlank;

public record CreateFloorRequest(
    @NotBlank String name,
    String description) {}
