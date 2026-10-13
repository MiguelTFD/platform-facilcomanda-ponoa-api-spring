package com.facilcomanda.ponoa.catalog;

import org.springframework.data.relational.core.mapping.Table;

@Table("product_categories")
public record ProductCategoryRef(Long categoryId) {}
