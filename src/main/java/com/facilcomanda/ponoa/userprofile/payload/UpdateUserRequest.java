package com.facilcomanda.ponoa.userprofile.payload;

import com.facilcomanda.ponoa.userprofile.AppUserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateUserRequest(
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    String firstName,
    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede exceder 100 caracteres")
    String lastName,
    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    String phoneNumber,
    String password,
    AppUserRole role,
    Set<AppUserRole> roles,
    Long branchId,
    Set<Long> branchIds,
    Boolean isActive) {

  public UpdateUserRequest(
      String firstName,
      String lastName,
      String phoneNumber,
      AppUserRole role,
      Set<AppUserRole> roles,
      Long branchId,
      Set<Long> branchIds,
      Boolean isActive) {
    this(firstName, lastName, phoneNumber, null, role, roles, branchId, branchIds, isActive);
  }

  public UpdateUserRequest(
      String firstName,
      String lastName,
      String phoneNumber,
      AppUserRole role,
      Long branchId,
      Boolean isActive) {
    this(firstName, lastName, phoneNumber, null, role, null, branchId, null, isActive);
  }
}
