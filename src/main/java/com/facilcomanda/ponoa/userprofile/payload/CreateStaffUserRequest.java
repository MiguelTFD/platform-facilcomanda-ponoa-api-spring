package com.facilcomanda.ponoa.userprofile.payload;

import com.facilcomanda.ponoa.userprofile.AppUserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateStaffUserRequest(
    UUID id,
    @NotBlank @Email String email,
    @NotBlank String firstName,
    @NotBlank String lastName,
    String phoneNumber,
    @NotNull AppUserRole role,
    Long branchId) {}
