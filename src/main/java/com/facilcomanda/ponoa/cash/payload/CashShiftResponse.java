package com.facilcomanda.ponoa.cash.payload;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CashShiftResponse(
    Long id,
    Long branchId,
    Long cashRegisterId,
    UUID cashierUserId,
    String cashierName,
    String status,
    BigDecimal openingAmount,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    BigDecimal closingCashCounted,
    BigDecimal expectedCashAmount,
    BigDecimal cashDifference,
    BigDecimal totalSalesAmount,
    BigDecimal totalCashCollected,
    BigDecimal totalChangeGiven,
    BigDecimal totalIncomes,
    BigDecimal totalExpenses,
    Map<String, BigDecimal> paymentsByMethod,
    String notes,
    List<CashMovementResponse> movements) {}
