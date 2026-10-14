package com.facilcomanda.ponoa.catalog;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("categories")
public record Category(
    @Id Long id,
    String name,
    String description,
    @Column("is_active") Boolean isActive,
    @Column("deleted_at") OffsetDateTime deletedAt) {

  @PersistenceCreator
  public Category {}

  public Category(Long id, String name, String description) {
    this(id, name, description, true, null);
  }
}
