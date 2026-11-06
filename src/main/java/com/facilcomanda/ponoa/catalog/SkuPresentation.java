package com.facilcomanda.ponoa.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("sku_presentations")
public record SkuPresentation(
    @Id Long id,
    @Column("sku_code") String skuCode,
    @Column("presentation_name") String presentationName,
    @Column("current_price") BigDecimal currentPrice,
    @Column("requires_stock") Boolean requiresStock,
    @Column("is_active") Boolean isActive,
    @Column("deleted_at") OffsetDateTime deletedAt) {

  @PersistenceCreator
  public SkuPresentation {
    if (requiresStock == null) {
      requiresStock = true;
    }
  }

  public SkuPresentation(
      Long id,
      String skuCode,
      String presentationName,
      BigDecimal currentPrice,
      Boolean isActive,
      OffsetDateTime deletedAt) {
    this(id, skuCode, presentationName, currentPrice, true, isActive, deletedAt);
  }

  public SkuPresentation(
      Long id,
      String skuCode,
      String presentationName,
      BigDecimal currentPrice,
      Boolean requiresStock) {
    this(id, skuCode, presentationName, currentPrice, requiresStock != null ? requiresStock : true, true, null);
  }

  public SkuPresentation(Long id, String skuCode, String presentationName, BigDecimal currentPrice) {
    this(id, skuCode, presentationName, currentPrice, true, true, null);
  }
}
