package com.facilcomanda.ponoa.sales;

import java.util.Set;

public record OrderItemsAddedEvent(
    Long branchId,
    Long orderId,
    Long tableId,
    String tableName,
    String orderType,
    String additionNotes,
    Set<AddedItemSummary> addedItems,
    Set<Long> addedSkuIds) {

  public record AddedItemSummary(
      Long skuId,
      Integer quantity,
      String comments) {}
}
