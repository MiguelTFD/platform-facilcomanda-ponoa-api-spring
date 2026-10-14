package com.facilcomanda.ponoa.cash.payload;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ZCutReportResponse(
    Long shiftId,
    Long branchId,
    String branchName,
    Long cashRegisterId,
    String cashRegisterName,
    UUID cashierUserId,
    String cashierName,
    String status,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    BigDecimal openingAmount,
    BigDecimal totalSalesAmount,
    Map<String, BigDecimal> salesByPaymentMethod,
    BigDecimal totalIncomes,
    BigDecimal totalExpenses,
    BigDecimal totalCashCollected,
    BigDecimal totalChangeGiven,
    BigDecimal netCashFromSales,
    BigDecimal expectedCash,
    BigDecimal countedCash,
    BigDecimal cashDifference,
    Long totalInvoicesCount,
    Map<String, Long> invoicesByType,
    String notes,
    List<CashMovementResponse> movements) {}
