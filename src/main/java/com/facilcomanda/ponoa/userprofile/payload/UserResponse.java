package com.facilcomanda.ponoa.userprofile.payload;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String phoneNumber,
    String role,
    Set<String> roles,
    Long branchId,
    Set<Long> branchIds,
    UUID createdBy,
    Boolean isActive,
    OffsetDateTime createdAt) {

  public UserResponse(
      UUID id,
      String email,
      String firstName,
      String lastName,
      String phoneNumber,
      String role,
      Long branchId,
      UUID createdBy,
      Boolean isActive,
      OffsetDateTime createdAt) {
    this(
        id,
        email,
        firstName,
        lastName,
        phoneNumber,
        role,
        role != null ? Set.of(role) : Set.of(),
        branchId,
        branchId != null ? Set.of(branchId) : Set.of(),
        createdBy,
        isActive,
        createdAt);
  }
}
