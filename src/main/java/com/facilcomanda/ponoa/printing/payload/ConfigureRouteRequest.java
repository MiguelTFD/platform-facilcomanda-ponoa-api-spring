package com.facilcomanda.ponoa.printing.payload;

import com.facilcomanda.ponoa.printing.PrintDocumentType;
import jakarta.validation.constraints.NotNull;

public record ConfigureRouteRequest(
    @NotNull PrintDocumentType documentType,
    String productionArea,
    Long productId) {

  public ConfigureRouteRequest(PrintDocumentType documentType, String productionArea) {
    this(documentType, productionArea, null);
  }
}

