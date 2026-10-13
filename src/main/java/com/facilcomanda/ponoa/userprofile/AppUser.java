package com.facilcomanda.ponoa.userprofile;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("app_users")
public record AppUser(
    @Id UUID id,
    String firstName,
    String lastName,
    String phoneNumber,
    @Column("created_at") OffsetDateTime createdAt,
    @MappedCollection(idColumn = "app_user_id") Set<IdentityDocument> documents,
    @MappedCollection(idColumn = "app_user_id") Set<Address> addresses) {}
