package com.facilcomanda.ponoa.catalog.payload;

import jakarta.validation.constraints.NotBlank;

public record UpdateProductProductionAreaRequest(
    @NotBlank(message = "El destino de impresión (productionArea) es obligatorio")
    String productionArea) {}
