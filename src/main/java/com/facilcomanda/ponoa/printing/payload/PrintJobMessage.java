package com.facilcomanda.ponoa.printing.payload;

import com.facilcomanda.ponoa.printing.PrintDocumentType;

public record PrintJobMessage(
    Long jobId,
    Long branchId,
    Long printerId,
    Long orderId,
    PrintDocumentType documentType,
    String ipAddress,
    Integer port) {}
