package com.facilcomanda.ponoa.billing.payload;

import com.facilcomanda.ponoa.billing.InvoiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PayOrderRequest(
    @NotNull(message = "orderId is required") Long orderId,
    @NotNull(message = "invoiceType is required") InvoiceType invoiceType,
    @Valid CustomerPayload customer,
    @NotEmpty(message = "At least one payment method is required") @Valid List<InvoicePaymentPayload> payments,
    String notes) {}
