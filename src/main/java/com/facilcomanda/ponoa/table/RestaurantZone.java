package com.facilcomanda.ponoa.table;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("restaurant_zones")
public record RestaurantZone(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("floor_id") Long floorId,
    String name,
    String description,
    @Column("is_active") Boolean isActive,
    @Column("deleted_at") OffsetDateTime deletedAt,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {

  @PersistenceCreator
  public RestaurantZone {}

  public RestaurantZone(
      Long id,
      Long branchId,
      Long floorId,
      String name,
      String description,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this(id, branchId, floorId, name, description, true, null, createdAt, updatedAt);
  }
}
