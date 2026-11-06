package com.facilcomanda.ponoa.table.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateZoneRequest(
    @NotNull(message = "El floorId es obligatorio")
    Long floorId,
    @NotBlank(message = "El nombre de la zona es obligatorio")
    @Size(max = 100, message = "El nombre de la zona no puede exceder 100 caracteres")
    String name,
    @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
    String description) {}
