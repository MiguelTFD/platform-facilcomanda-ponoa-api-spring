package com.facilcomanda.ponoa.cash.payload;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CashMovementResponse(
    Long id,
    Long cashShiftId,
    String type,
    BigDecimal amount,
    String reason,
    UUID performedByUserId,
    OffsetDateTime createdAt) {}
