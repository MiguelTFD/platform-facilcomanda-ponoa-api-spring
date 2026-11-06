package com.facilcomanda.ponoa.table.payload;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTableRequest(
    Long floorId,
    Long zoneId,
    @NotBlank(message = "El nombre de la mesa es obligatorio")
    @Size(max = 50, message = "El nombre no puede exceder 50 caracteres")
    String name,
    @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
    String description,
    @NotNull(message = "La cantidad de sillas es obligatoria")
    @Min(value = 1, message = "Debe tener al menos 1 silla")
    Integer chairs) {

  public UpdateTableRequest(
      Long floorId,
      String name,
      String description,
      Integer chairs) {
    this(floorId, null, name, description, chairs);
  }
}
