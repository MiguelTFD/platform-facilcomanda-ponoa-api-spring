package com.facilcomanda.ponoa.userprofile.payload;

import com.facilcomanda.ponoa.userprofile.AppUserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.UUID;

public record CreateAdminUserRequest(
    UUID id,
    @NotBlank @Email String email,
    String password,
    @NotBlank String firstName,
    @NotBlank String lastName,
    String phoneNumber,
    Long branchId,
    Set<Long> branchIds,
    Set<AppUserRole> roles) {

  public CreateAdminUserRequest(
      UUID id,
      String email,
      String password,
      String firstName,
      String lastName,
      String phoneNumber,
      Long branchId) {
    this(id, email, password, firstName, lastName, phoneNumber, branchId, null, null);
  }

  public CreateAdminUserRequest(
      UUID id,
      String email,
      String firstName,
      String lastName,
      String phoneNumber,
      Long branchId) {
    this(id, email, null, firstName, lastName, phoneNumber, branchId, null, null);
  }
}
