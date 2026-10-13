package com.facilcomanda.ponoa.catalog;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("sku_presentations")
public record SkuPresentation(
    @Id Long id, String skuCode, String presentationName, BigDecimal currentPrice) {}
