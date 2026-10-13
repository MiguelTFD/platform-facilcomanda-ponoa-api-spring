package com.facilcomanda.ponoa.billing.payload;

import com.facilcomanda.ponoa.billing.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record InvoicePaymentPayload(
    @NotNull(message = "Payment method is required") PaymentMethod method,
    @NotNull(message = "Payment amount is required") @Positive BigDecimal amount,
    String reference) {}
