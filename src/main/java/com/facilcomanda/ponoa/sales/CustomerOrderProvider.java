package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CustomerOrderProvider {

  Optional<OrderPrintSummary> getOrderPrintSummary(Long branchId, Long orderId);

  Optional<OrderBillingSummary> getOrderBillingSummary(Long branchId, Long orderId);

  void markOrderAsPaid(Long branchId, Long orderId);

  Optional<OrderPreCheckSummary> getOrderPreCheckSummary(Long branchId, Long orderId);

  Optional<OrderPreCheckSummary> getActiveOrderPreCheckSummaryByTable(Long branchId, Long tableId);

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
      Set<Long> skuIds,
      List<OrderPrintItem> items) {

    public OrderPrintSummary(
        Long orderId,
        Long branchId,
        Long tableId,
        String tableName,
        String orderType,
        String notes,
        Set<Long> skuIds) {
      this(orderId, branchId, tableId, tableName, orderType, notes, skuIds, List.of());
    }
  }

  record OrderPrintItem(
      Long skuId,
      String name,
      Integer quantity,
      String productionArea,
      String comments) {}

  record OrderPreCheckSummary(
      Long orderId,
      Long branchId,
      Long tableId,
      String tableName,
      String orderType,
      String waiterOrCashierEmail,
      BigDecimal totalAmount,
      BigDecimal subtotal,
      BigDecimal taxAmount,
      BigDecimal suggestedTip10,
      List<PreCheckItem> items) {}

  record PreCheckItem(
      Long skuId,
      String name,
      Integer quantity,
      BigDecimal unitPrice,
      BigDecimal lineTotal,
      String comments) {}
}
