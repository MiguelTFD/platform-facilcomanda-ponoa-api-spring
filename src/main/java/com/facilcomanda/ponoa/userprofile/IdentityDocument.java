package com.facilcomanda.ponoa.userprofile;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("identity_documents")
public record IdentityDocument(@Id Long id, String documentType, String documentNumber) {}
