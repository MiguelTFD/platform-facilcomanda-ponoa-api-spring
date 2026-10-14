package com.facilcomanda.ponoa.report.payload;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record SalesSummaryResponse(
    Long branchId,
    OffsetDateTime from,
    OffsetDateTime to,
    BigDecimal totalSales,
    BigDecimal netAmount,
    BigDecimal taxAmount,
    Long invoiceCount,
    Map<String, BigDecimal> salesByPaymentMethod,
    Map<String, BigDecimal> salesByInvoiceType) {}
