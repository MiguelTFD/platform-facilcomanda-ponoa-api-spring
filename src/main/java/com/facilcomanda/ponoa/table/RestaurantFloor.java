package com.facilcomanda.ponoa.table;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("restaurant_floors")
public record RestaurantFloor(
    @Id Long id,
    @Column("branch_id") Long branchId,
    String name,
    String description,
    @Column("is_active") Boolean isActive,
    @Column("deleted_at") OffsetDateTime deletedAt,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {

  @PersistenceCreator
  public RestaurantFloor {}

  public RestaurantFloor(
      Long id,
      Long branchId,
      String name,
      String description,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this(id, branchId, name, description, true, null, createdAt, updatedAt);
  }
}
