package com.facilcomanda.ponoa.table;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("restaurant_floors")
public record RestaurantFloor(
    @Id Long id,
    @Column("branch_id") Long branchId,
    String name,
    String description,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
