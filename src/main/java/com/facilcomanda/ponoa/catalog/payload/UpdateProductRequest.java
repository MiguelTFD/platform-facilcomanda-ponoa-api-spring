package com.facilcomanda.ponoa.catalog.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Set;

public record UpdateProductRequest(
    @NotNull(message = "El ID de marca es obligatorio") Long brandId,
    @NotBlank(message = "El nombre del producto es obligatorio") String name,
    String baseDescription,
    String productionArea,
    Set<Long> categoryIds,
    @Valid Set<SkuUpdateRequest> skus) {

  public UpdateProductRequest(
      Long brandId,
      String name,
      String baseDescription,
      String productionArea,
      Set<Long> categoryIds) {
    this(brandId, name, baseDescription, productionArea, categoryIds, null);
  }

  public record SkuUpdateRequest(
      Long id,
      @NotBlank(message = "El código SKU es obligatorio") String skuCode,
      @NotBlank(message = "El nombre de presentación es obligatorio") String presentationName,
      @NotNull(message = "El precio es obligatorio") @PositiveOrZero(message = "El precio debe ser mayor o igual a 0") BigDecimal currentPrice,
      Boolean isActive,
      Boolean requiresStock) {

    public SkuUpdateRequest(
        Long id,
        String skuCode,
        String presentationName,
        BigDecimal currentPrice,
        Boolean isActive) {
      this(id, skuCode, presentationName, currentPrice, isActive, null);
    }
  }
}
