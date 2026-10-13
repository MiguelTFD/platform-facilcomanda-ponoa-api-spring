package com.facilcomanda.ponoa.billing;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("invoice_series")
public record InvoiceSeries(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("invoice_type") String invoiceType,
    String series,
    @Column("current_number") Long currentNumber,
    @Column("updated_at") OffsetDateTime updatedAt) {}
