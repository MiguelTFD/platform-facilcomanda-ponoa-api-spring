package com.facilcomanda.ponoa.billing.payload;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
    Long id,
    Long branchId,
    Long cashShiftId,
    Long orderId,
    Long customerId,
    String customerName,
    String customerDocumentType,
    String customerDocumentNumber,
    String invoiceNumber,
    String invoiceType,
    BigDecimal orderTotal,
    BigDecimal amountPaid,
    BigDecimal changeAmount,
    UUID cashierUserId,
    String cashierEmail,
    Long restaurantTableId,
    String restaurantTableName,
    String notes,
    OffsetDateTime paidAt,
    List<InvoicePaymentResponse> payments) {

  public record InvoicePaymentResponse(
      Long id,
      String method,
      BigDecimal amount,
      String reference) {}
}
