package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import java.util.List;

public record PreCheckResponse(
    Long orderId,
    Long branchId,
    Long tableId,
    String tableName,
    String orderType,
    BigDecimal subtotal,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    BigDecimal suggestedTip10,
    List<PreCheckItemDto> items) {

  public record PreCheckItemDto(
      Long skuId,
      String name,
      Integer quantity,
      BigDecimal unitPrice,
      BigDecimal lineTotal,
      String comments) {}
}
