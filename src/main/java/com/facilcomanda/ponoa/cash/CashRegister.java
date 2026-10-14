package com.facilcomanda.ponoa.cash;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("cash_registers")
public record CashRegister(
    @Id Long id,
    @Column("branch_id") Long branchId,
    String name,
    String description,
    @Column("is_active") Boolean isActive,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
