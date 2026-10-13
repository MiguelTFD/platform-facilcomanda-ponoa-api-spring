package com.facilcomanda.ponoa.printing;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("printer_routes")
public record PrinterRoute(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("printer_id") Long printerId,
    @Column("document_type") PrintDocumentType documentType,
    @Column("production_area") String productionArea,
    @Column("created_at") OffsetDateTime createdAt) {}
