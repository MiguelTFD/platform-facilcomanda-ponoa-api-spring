package com.facilcomanda.ponoa.userprofile.payload;

import com.facilcomanda.ponoa.userprofile.AppUserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.UUID;

public record CreateStaffUserRequest(
    UUID id,
    @NotBlank @Email String email,
    String password,
    @NotBlank String firstName,
    @NotBlank String lastName,
    String phoneNumber,
    AppUserRole role,
    Set<AppUserRole> roles,
    Long branchId,
    Set<Long> branchIds) {

  public CreateStaffUserRequest(
      UUID id,
      String email,
      String password,
      String firstName,
      String lastName,
      String phoneNumber,
      AppUserRole role,
      Long branchId) {
    this(id, email, password, firstName, lastName, phoneNumber, role, null, branchId, null);
  }

  public CreateStaffUserRequest(
      UUID id,
      String email,
      String firstName,
      String lastName,
      String phoneNumber,
      AppUserRole role,
      Long branchId) {
    this(id, email, null, firstName, lastName, phoneNumber, role, null, branchId, null);
  }
}
