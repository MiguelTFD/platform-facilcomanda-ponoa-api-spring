package com.facilcomanda.ponoa.catalog;

import java.util.Set;
import org.springframework.data.annotation.Id;
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
    @MappedCollection(idColumn = "product_id") Set<SkuPresentation> skus,
    @MappedCollection(idColumn = "product_id") Set<ProductPicture> pictures,
    @MappedCollection(idColumn = "product_id") Set<ProductCategoryRef> categoryRefs) {}
