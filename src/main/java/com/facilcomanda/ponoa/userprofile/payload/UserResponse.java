package com.facilcomanda.ponoa.userprofile.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String phoneNumber,
    String role,
    Long branchId,
    UUID createdBy,
    Boolean isActive,
    OffsetDateTime createdAt) {}
