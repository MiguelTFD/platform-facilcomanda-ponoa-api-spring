package com.facilcomanda.ponoa.catalog.payload;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ConfigureBranchProductRequest(
    @NotNull Long skuId,
    @NotNull @DecimalMin("0.00") BigDecimal price,
    @NotNull Boolean isAvailable) {}
