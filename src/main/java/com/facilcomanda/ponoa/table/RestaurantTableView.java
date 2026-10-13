package com.facilcomanda.ponoa.table;

public record RestaurantTableView(
    Long id,
    Long branchId,
    Long floorId,
    String tableName,
    String floorName,
    TableState state) {}
