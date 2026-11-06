package com.facilcomanda.ponoa.billing.payload;

import java.time.OffsetDateTime;

public record CustomerResponse(
    Long id,
    String name,
    String identityDocumentType,
    String identityDocumentNumber,
    String phoneNumber,
    String address,
    Boolean isActive,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
