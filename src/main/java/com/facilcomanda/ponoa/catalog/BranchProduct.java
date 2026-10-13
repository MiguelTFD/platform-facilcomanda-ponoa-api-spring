package com.facilcomanda.ponoa.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("branch_products")
public record BranchProduct(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("sku_id") Long skuId,
    BigDecimal price,
    @Column("is_available") Boolean isAvailable,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
