package com.facilcomanda.ponoa.printing.payload;

import com.facilcomanda.ponoa.printing.PrintDocumentType;

public record PrintJobMessage(
    Long jobId,
    Long branchId,
    Long printerId,
    Long orderId,
    PrintDocumentType documentType,
    String ipAddress,
    Integer port,
    String targetArea) {

  public PrintJobMessage(
      Long jobId,
      Long branchId,
      Long printerId,
      Long orderId,
      PrintDocumentType documentType,
      String ipAddress,
      Integer port) {
    this(jobId, branchId, printerId, orderId, documentType, ipAddress, port, null);
  }
}
