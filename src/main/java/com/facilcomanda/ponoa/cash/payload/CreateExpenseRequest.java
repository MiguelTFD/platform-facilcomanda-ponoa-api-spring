package com.facilcomanda.ponoa.cash.payload;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateExpenseRequest(
    Long shiftId,
    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    BigDecimal amount,
    @NotBlank(message = "El motivo del gasto es obligatorio")
    @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
    String reason) {}
