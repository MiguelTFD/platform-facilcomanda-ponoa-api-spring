package com.facilcomanda.ponoa.table;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("restaurant_tables")
public record RestaurantTable(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("floor_id") Long floorId,
    @Column("zone_id") Long zoneId,
    String name,
    String description,
    TableState state,
    Integer chairs,
    @Column("is_active") Boolean isActive,
    @Column("deleted_at") OffsetDateTime deletedAt,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {

  @PersistenceCreator
  public RestaurantTable {}

  public RestaurantTable(
      Long id,
      Long branchId,
      Long floorId,
      Long zoneId,
      String name,
      String description,
      TableState state,
      Integer chairs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this(
        id,
        branchId,
        floorId,
        zoneId,
        name,
        description,
        state,
        chairs,
        true,
        null,
        createdAt,
        updatedAt);
  }

  public RestaurantTable(
      Long id,
      Long branchId,
      Long floorId,
      String name,
      String description,
      TableState state,
      Integer chairs,
      Boolean isActive,
      OffsetDateTime deletedAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this(
        id,
        branchId,
        floorId,
        null,
        name,
        description,
        state,
        chairs,
        isActive,
        deletedAt,
        createdAt,
        updatedAt);
  }

  public RestaurantTable(
      Long id,
      Long branchId,
      Long floorId,
      String name,
      String description,
      TableState state,
      Integer chairs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this(
        id,
        branchId,
        floorId,
        null,
        name,
        description,
        state,
        chairs,
        true,
        null,
        createdAt,
        updatedAt);
  }
}
