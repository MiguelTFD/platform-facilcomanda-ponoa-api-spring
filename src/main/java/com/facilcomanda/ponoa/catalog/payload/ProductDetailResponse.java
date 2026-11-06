package com.facilcomanda.ponoa.catalog.payload;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailResponse(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String baseDescription,
    String productionArea,
    Boolean isActive,
    List<CategoryItem> categories,
    List<SkuItem> skus) {

  public record CategoryItem(Long id, String name) {}

  public record SkuItem(
      Long id,
      String skuCode,
      String presentationName,
      BigDecimal currentPrice,
      Boolean isActive,
      Boolean requiresStock) {

    public SkuItem(
        Long id,
        String skuCode,
        String presentationName,
        BigDecimal currentPrice,
        Boolean isActive) {
      this(id, skuCode, presentationName, currentPrice, isActive, true);
    }
  }
}
