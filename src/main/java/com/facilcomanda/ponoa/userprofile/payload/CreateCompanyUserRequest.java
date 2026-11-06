package com.facilcomanda.ponoa.userprofile.payload;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateCompanyUserRequest(
    UUID id,
    @NotBlank @Email String email,
    String password,
    @NotBlank String firstName,
    @NotBlank String lastName,
    String phoneNumber) {

  public CreateCompanyUserRequest(
      UUID id,
      String email,
      String firstName,
      String lastName,
      String phoneNumber) {
    this(id, email, null, firstName, lastName, phoneNumber);
  }
}
