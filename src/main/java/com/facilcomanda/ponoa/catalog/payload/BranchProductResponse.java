package com.facilcomanda.ponoa.catalog.payload;

import java.math.BigDecimal;

public record BranchProductResponse(
    Long id,
    Long branchId,
    Long skuId,
    String skuCode,
    String productName,
    String presentationName,
    BigDecimal price,
    Boolean isAvailable,
    String productionArea,
    Boolean requiresStock) {

  public BranchProductResponse(
      Long id,
      Long branchId,
      Long skuId,
      String skuCode,
      String productName,
      String presentationName,
      BigDecimal price,
      Boolean isAvailable) {
    this(
        id,
        branchId,
        skuId,
        skuCode,
        productName,
        presentationName,
        price,
        isAvailable,
        "COCINA",
        true);
  }
}
