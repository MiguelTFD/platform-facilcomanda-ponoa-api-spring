package com.facilcomanda.ponoa.printing.payload;

import com.facilcomanda.ponoa.printing.PrintDocumentType;
import jakarta.validation.constraints.NotNull;

public record ConfigureRouteRequest(@NotNull PrintDocumentType documentType) {}
