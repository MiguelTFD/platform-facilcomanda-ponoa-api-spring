package com.facilcomanda.ponoa.report.payload;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ProductSalesRankingResponse(
    Long branchId,
    OffsetDateTime from,
    OffsetDateTime to,
    List<ProductSaleItem> items) {

  public record ProductSaleItem(
      Long skuId,
      String skuCode,
      String productName,
      String presentationName,
      Long quantitySold,
      BigDecimal totalRevenue) {}
}
