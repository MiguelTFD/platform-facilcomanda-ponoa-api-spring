package com.facilcomanda.ponoa.billing;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("customers")
public record Customer(
    @Id Long id,
    String name,
    @Column("identity_document_type") String identityDocumentType,
    @Column("identity_document_number") String identityDocumentNumber,
    @Column("phone_number") String phoneNumber,
    String address,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
