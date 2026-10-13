package com.facilcomanda.ponoa.printing.payload;

public record PrinterDeviceResponse(
    Long id,
    Long branchId,
    String name,
    String ipAddress,
    Integer port,
    String connectionType,
    String terminalCode,
    Boolean isActive) {}
