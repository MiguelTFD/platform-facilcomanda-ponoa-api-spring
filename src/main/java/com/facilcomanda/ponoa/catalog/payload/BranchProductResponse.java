package com.facilcomanda.ponoa.catalog.payload;

import java.math.BigDecimal;

public record BranchProductResponse(
    Long id,
    Long branchId,
    Long skuId,
    String skuCode,
    String productName,
    String presentationName,
    BigDecimal price,
    Boolean isAvailable) {}
