package com.facilcomanda.ponoa.catalog.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateProductRequest(
    @NotNull(message = "El ID de marca es obligatorio") Long brandId,
    @NotBlank(message = "El nombre del producto es obligatorio") String name,
    String baseDescription,
    String productionArea,
    Set<Long> categoryIds) {}
