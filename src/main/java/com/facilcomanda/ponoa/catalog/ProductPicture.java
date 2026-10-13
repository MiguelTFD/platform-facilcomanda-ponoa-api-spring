package com.facilcomanda.ponoa.catalog;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("product_pictures")
public record ProductPicture(@Id Long id, String imageUrl, boolean isPrimary) {}
