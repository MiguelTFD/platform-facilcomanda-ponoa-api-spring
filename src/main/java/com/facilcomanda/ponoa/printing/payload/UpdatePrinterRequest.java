package com.facilcomanda.ponoa.printing.payload;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePrinterRequest(
    @NotBlank(message = "El nombre de la impresora es obligatorio")
    @Size(max = 100)
    String name,
    @NotBlank(message = "La dirección IP o host es obligatorio")
    @Size(max = 255)
    String ipAddress,
    @Min(1) @Max(65535)
    Integer port,
    @Size(max = 50)
    String connectionType,
    @Size(max = 50)
    String terminalCode,
    Boolean isActive) {}
