package com.facilcomanda.ponoa.branch;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("branches")
public record Branch(
    @Id Long id,
    String name,
    String code,
    String address,
    @Column("phone_number") String phoneNumber,
    @Column("is_active") Boolean isActive,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
