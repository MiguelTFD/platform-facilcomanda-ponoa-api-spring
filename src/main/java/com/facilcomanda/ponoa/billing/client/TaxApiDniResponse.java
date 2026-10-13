package com.facilcomanda.ponoa.billing.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaxApiDniResponse(
    Boolean success,
    String message,
    DniDatos datos) {

  public record DniDatos(
      String dni,
      String nombres,
      @JsonProperty("ape_paterno") String apePaterno,
      @JsonProperty("ape_materno") String apeMaterno) {

    public String fullName() {
      StringBuilder sb = new StringBuilder();
      if (nombres != null && !nombres.isBlank()) {
        sb.append(nombres.trim());
      }
      if (apePaterno != null && !apePaterno.isBlank()) {
        if (!sb.isEmpty()) sb.append(" ");
        sb.append(apePaterno.trim());
      }
      if (apeMaterno != null && !apeMaterno.isBlank()) {
        if (!sb.isEmpty()) sb.append(" ");
        sb.append(apeMaterno.trim());
      }
      return sb.toString();
    }
  }
}
