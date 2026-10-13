package com.facilcomanda.ponoa.inventory;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("stock_levels")
public record StockLevel(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("sku_id") Long skuId,
    Integer quantity,
    @Column("updated_at") OffsetDateTime updatedAt) {}
