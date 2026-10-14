package com.facilcomanda.ponoa.auth.payload;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
    @NotBlank(message = "El refreshToken es obligatorio")
    String refreshToken) {}
