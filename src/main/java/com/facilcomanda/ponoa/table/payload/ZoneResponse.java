package com.facilcomanda.ponoa.table.payload;

public record ZoneResponse(
    Long id,
    Long branchId,
    Long floorId,
    String name,
    String description) {}
