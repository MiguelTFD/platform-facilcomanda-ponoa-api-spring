package com.facilcomanda.ponoa.inventory;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("stock_levels")
public record StockLevel(@Id Long skuId, Integer quantity, OffsetDateTime updatedAt) {}
