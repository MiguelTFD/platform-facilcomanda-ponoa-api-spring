package com.facilcomanda.ponoa.catalog;

import java.math.BigDecimal;

public record ProductSummaryView(
    Long id, String name, String brandName, BigDecimal startingPrice) {}
