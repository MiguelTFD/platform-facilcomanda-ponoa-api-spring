package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

public interface CustomerOrderProvider {

  Optional<OrderPrintSummary> getOrderPrintSummary(Long branchId, Long orderId);

  Optional<OrderBillingSummary> getOrderBillingSummary(Long branchId, Long orderId);

  void markOrderAsPaid(Long branchId, Long orderId);

  record OrderBillingSummary(
      Long orderId,
      Long branchId,
      Long tableId,
      String tableName,
      String orderType,
      BigDecimal totalAmount,
      String status) {}

  record OrderPrintSummary(
      Long orderId,
      Long branchId,
      Long tableId,
      String tableName,
      String orderType,
      String notes,
      Set<Long> skuIds) {}
}
