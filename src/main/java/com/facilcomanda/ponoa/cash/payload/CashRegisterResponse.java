package com.facilcomanda.ponoa.cash.payload;

import java.time.OffsetDateTime;

public record CashRegisterResponse(
    Long id,
    Long branchId,
    String name,
    String description,
    Boolean isActive,
    OffsetDateTime createdAt) {}
