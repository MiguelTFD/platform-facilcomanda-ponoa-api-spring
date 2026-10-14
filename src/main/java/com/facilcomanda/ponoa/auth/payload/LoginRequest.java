package com.facilcomanda.ponoa.auth.payload;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "El email o usuario es obligatorio")
    String email,
    @NotBlank(message = "La contraseña es obligatoria")
    String password) {}
