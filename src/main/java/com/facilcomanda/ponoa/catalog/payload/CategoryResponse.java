package com.facilcomanda.ponoa.catalog.payload;

public record CategoryResponse(
    Long id,
    String name,
    String description,
    Boolean isActive) {}
