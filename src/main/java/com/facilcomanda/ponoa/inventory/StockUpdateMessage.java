package com.facilcomanda.ponoa.inventory;

public record StockUpdateMessage(
    Long branchId,
    Long skuId,
    Integer remainingStock,
    boolean isAvailable) {}
