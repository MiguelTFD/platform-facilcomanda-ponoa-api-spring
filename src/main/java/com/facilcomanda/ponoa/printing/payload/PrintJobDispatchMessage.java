package com.facilcomanda.ponoa.printing.payload;

import com.facilcomanda.ponoa.printing.PrintDocumentType;
import java.time.Instant;

public record PrintJobDispatchMessage(
    Long jobId,
    Long branchId,
    Long printerId,
    Long orderId,
    PrintDocumentType documentType,
    String ipAddress,
    Integer port,
    String targetArea,
    String tableName,
    String orderType,
    String payloadBase64,
    Instant timestamp) {}
