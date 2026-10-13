package com.facilcomanda.ponoa.catalog.payload;

import java.math.BigDecimal;

public record BranchProductResponse(
    Long id,
    Long branchId,
    Long skuId,
    BigDecimal price,
    Boolean isAvailable) {}
