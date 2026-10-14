package com.facilcomanda.ponoa.branch.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBranchRequest(
    @NotBlank(message = "El nombre de la sucursal es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    String name,
    @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
    String address,
    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    String phoneNumber,
    Boolean isActive) {}
