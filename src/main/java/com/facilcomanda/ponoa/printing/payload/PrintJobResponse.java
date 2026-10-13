package com.facilcomanda.ponoa.printing.payload;

import com.facilcomanda.ponoa.printing.PrintDocumentType;
import com.facilcomanda.ponoa.printing.PrintJobStatus;
import java.time.OffsetDateTime;

public record PrintJobResponse(
    Long id,
    Long branchId,
    Long printerId,
    Long orderId,
    PrintDocumentType documentType,
    PrintJobStatus status,
    String errorMessage,
    Integer retryCount,
    OffsetDateTime createdAt) {}
