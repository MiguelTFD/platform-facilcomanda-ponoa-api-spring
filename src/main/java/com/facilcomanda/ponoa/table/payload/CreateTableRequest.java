package com.facilcomanda.ponoa.table.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTableRequest(
    Long floorId,
    Long zoneId,
    @NotBlank String name,
    String description,
    @Positive Integer chairs) {

  public CreateTableRequest(
      Long floorId,
      String name,
      String description,
      Integer chairs) {
    this(floorId, null, name, description, chairs);
  }
}
