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
    String email,
    @Column("first_name") String firstName,
    @Column("last_name") String lastName,
    @Column("phone_number") String phoneNumber,
    String role,
    @Column("branch_id") Long branchId,
    @Column("created_by") UUID createdBy,
    @Column("is_active") Boolean isActive,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt,
    @MappedCollection(idColumn = "app_user_id") Set<IdentityDocument> documents,
    @MappedCollection(idColumn = "app_user_id") Set<Address> addresses) {

  public AppUser(
      UUID id,
      String firstName,
      String lastName,
      String phoneNumber,
      OffsetDateTime createdAt,
      Set<IdentityDocument> documents,
      Set<Address> addresses) {
    this(
        id,
        null,
        firstName,
        lastName,
        phoneNumber,
        "MESERO",
        null,
        null,
        true,
        createdAt,
        null,
        documents,
        addresses);
  }
}
