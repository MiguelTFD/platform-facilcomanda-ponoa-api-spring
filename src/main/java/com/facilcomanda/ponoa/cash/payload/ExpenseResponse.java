package com.facilcomanda.ponoa.cash.payload;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExpenseResponse(
    Long id,
    Long branchId,
    Long shiftId,
    BigDecimal amount,
    String reason,
    UUID registeredByUserId,
    String registeredByName,
    OffsetDateTime createdAt) {}
