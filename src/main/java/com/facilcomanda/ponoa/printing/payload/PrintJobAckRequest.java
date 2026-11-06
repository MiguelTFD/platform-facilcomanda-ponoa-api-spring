package com.facilcomanda.ponoa.printing.payload;

import com.facilcomanda.ponoa.printing.PrintJobStatus;
import jakarta.validation.constraints.NotNull;

public record PrintJobAckRequest(
    @NotNull(message = "El status es obligatorio (COMPLETED o FAILED)")
    PrintJobStatus status,
    String errorMessage) {}
