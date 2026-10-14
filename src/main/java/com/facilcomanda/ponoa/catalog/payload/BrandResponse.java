package com.facilcomanda.ponoa.catalog.payload;

public record BrandResponse(
    Long id,
    String name,
    String description,
    Boolean isActive) {}
