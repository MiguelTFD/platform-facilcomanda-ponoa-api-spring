package com.facilcomanda.ponoa.billing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("invoice_payments")
public record InvoicePayment(
    @Id Long id,
    @Column("invoice_id") Long invoiceId,
    String method,
    BigDecimal amount,
    String reference,
    @Column("created_at") OffsetDateTime createdAt) {

  public InvoicePayment(String method, BigDecimal amount, String reference) {
    this(null, null, method, amount, reference, null);
  }
}
