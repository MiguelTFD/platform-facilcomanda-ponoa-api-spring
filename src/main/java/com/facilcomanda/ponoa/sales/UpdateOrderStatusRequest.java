package com.facilcomanda.ponoa.sales;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrderStatusRequest(
    @NotBlank(message = "El nuevo estado de la orden es requerido")
    String status) {}
