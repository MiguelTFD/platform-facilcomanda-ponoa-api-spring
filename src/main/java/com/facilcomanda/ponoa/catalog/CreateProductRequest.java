package com.facilcomanda.ponoa.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Set;

public record CreateProductRequest(
    @NotNull Long brandId,
    @NotBlank String name,
    String baseDescription,
    String productionArea,
    Set<Long> categoryIds,
    Set<SkuPayload> skus) {
  public record SkuPayload(
      @NotBlank String skuCode,
      @NotBlank String presentationName,
      @NotNull @PositiveOrZero BigDecimal currentPrice,
      Boolean requiresStock) {

    public SkuPayload {
      if (requiresStock == null) {
        requiresStock = true;
      }
    }

    public SkuPayload(
        String skuCode, String presentationName, BigDecimal currentPrice) {
      this(skuCode, presentationName, currentPrice, true);
    }
  }
}
