package com.facilcomanda.ponoa.billing.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaxApiRucResponse(
    Boolean success,
    String message,
    RucDatos datos) {

  public record RucDatos(
      String ruc,
      @JsonProperty("razon_social") String razonSocial,
      String estado,
      String condicion,
      String direccion) {}
}
