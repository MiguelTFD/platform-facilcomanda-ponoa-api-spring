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
    @Column("is_active") Boolean isActive,
    @Column("deleted_at") OffsetDateTime deletedAt) {

  @PersistenceCreator
  public SkuPresentation {}

  public SkuPresentation(Long id, String skuCode, String presentationName, BigDecimal currentPrice) {
    this(id, skuCode, presentationName, currentPrice, true, null);
  }
}
