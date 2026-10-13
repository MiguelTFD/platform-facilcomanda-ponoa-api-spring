package com.facilcomanda.ponoa.userprofile.payload;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateCompanyUserRequest(
    UUID id,
    @NotBlank @Email String email,
    @NotBlank String firstName,
    @NotBlank String lastName,
    String phoneNumber) {}
