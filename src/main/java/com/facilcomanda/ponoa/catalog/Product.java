package com.facilcomanda.ponoa.catalog;

import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("products")
public record Product(
    @Id Long id,
    @Column("brand_id") Long brandId,
    String name,
    String baseDescription,
    @Column("production_area") String productionArea,
    @Column("is_active") Boolean isActive,
    @Column("deleted_at") OffsetDateTime deletedAt,
    @MappedCollection(idColumn = "product_id") Set<SkuPresentation> skus,
    @MappedCollection(idColumn = "product_id") Set<ProductPicture> pictures,
    @MappedCollection(idColumn = "product_id") Set<ProductCategoryRef> categoryRefs) {

  @PersistenceCreator
  public Product {}

  public Product(
      Long id,
      Long brandId,
      String name,
      String baseDescription,
      String productionArea,
      Set<SkuPresentation> skus,
      Set<ProductPicture> pictures,
      Set<ProductCategoryRef> categoryRefs) {
    this(
        id,
        brandId,
        name,
        baseDescription,
        productionArea,
        true,
        null,
        skus,
        pictures,
        categoryRefs);
  }
}
