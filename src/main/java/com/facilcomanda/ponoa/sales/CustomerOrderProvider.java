package com.facilcomanda.ponoa.sales;

import java.util.Optional;
import java.util.Set;

public interface CustomerOrderProvider {

  Optional<OrderPrintSummary> getOrderPrintSummary(Long branchId, Long orderId);

  record OrderPrintSummary(
      Long orderId,
      Long branchId,
      Long tableId,
      String tableName,
      String orderType,
      String notes,
      Set<Long> skuIds) {}
}
