package com.facilcomanda.ponoa.cash.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCashRegisterRequest(
    @NotBlank(message = "El nombre de la caja es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    String name,
    @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
    String description,
    Boolean isActive) {}
