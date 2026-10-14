package com.facilcomanda.ponoa.printing.payload;

import java.time.Instant;

public record PrinterFailureAlert(
    Long branchId,
    Long jobId,
    Long orderId,
    String documentType,
    String ipAddress,
    int port,
    String targetArea,
    String errorMessage,
    String actionRequired,
    Instant timestamp) {}
