package com.facilcomanda.ponoa.table;

import org.springframework.data.annotation.PersistenceCreator;

public record RestaurantTableView(
    Long id,
    Long branchId,
    Long floorId,
    Long zoneId,
    String tableName,
    String floorName,
    String zoneName,
    TableState state) {

  @PersistenceCreator
  public RestaurantTableView {}

  public RestaurantTableView(
      Long id,
      Long branchId,
      Long floorId,
      String tableName,
      String floorName,
      TableState state) {
    this(id, branchId, floorId, null, tableName, floorName, null, state);
  }
}
