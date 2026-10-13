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
    String targetArea,
    String tableName,
    String orderType,
    String notes,
    boolean isReprint,
    boolean isAddition) {

  public PrintJobMessage(
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
      String notes,
      boolean isReprint) {
    this(
        jobId,
        branchId,
        printerId,
        orderId,
        documentType,
        ipAddress,
        port,
        targetArea,
        tableName,
        orderType,
        notes,
        isReprint,
        false);
  }

  public PrintJobMessage(
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
      String notes) {
    this(
        jobId,
        branchId,
        printerId,
        orderId,
        documentType,
        ipAddress,
        port,
        targetArea,
        tableName,
        orderType,
        notes,
        false,
        false);
  }

  public PrintJobMessage(
      Long jobId,
      Long branchId,
      Long printerId,
      Long orderId,
      PrintDocumentType documentType,
      String ipAddress,
      Integer port,
      String targetArea) {
    this(
        jobId,
        branchId,
        printerId,
        orderId,
        documentType,
        ipAddress,
        port,
        targetArea,
        null,
        "DINE_IN",
        null,
        false,
        false);
  }

  public PrintJobMessage(
      Long jobId,
      Long branchId,
      Long printerId,
      Long orderId,
      PrintDocumentType documentType,
      String ipAddress,
      Integer port) {
    this(
        jobId,
        branchId,
        printerId,
        orderId,
        documentType,
        ipAddress,
        port,
        null,
        null,
        "DINE_IN",
        null,
        false,
        false);
  }
}
