package com.facilcomanda.ponoa.inventory.payload;

import java.time.OffsetDateTime;

public record StockResponse(
    Long id,
    Long branchId,
    Long skuId,
    Integer quantity,
    OffsetDateTime updatedAt) {}
