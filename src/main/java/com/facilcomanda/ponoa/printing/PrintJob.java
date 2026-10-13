package com.facilcomanda.ponoa.printing;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("print_jobs")
public record PrintJob(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("printer_id") Long printerId,
    @Column("order_id") Long orderId,
    @Column("document_type") PrintDocumentType documentType,
    @Column("idempotency_key") String idempotencyKey,
    PrintJobStatus status,
    @Column("error_message") String errorMessage,
    @Column("retry_count") Integer retryCount,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
