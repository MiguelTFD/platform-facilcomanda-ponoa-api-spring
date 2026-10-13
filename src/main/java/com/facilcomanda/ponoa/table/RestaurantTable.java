package com.facilcomanda.ponoa.table;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("restaurant_tables")
public record RestaurantTable(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("floor_id") Long floorId,
    String name,
    String description,
    TableState state,
    Integer chairs,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
