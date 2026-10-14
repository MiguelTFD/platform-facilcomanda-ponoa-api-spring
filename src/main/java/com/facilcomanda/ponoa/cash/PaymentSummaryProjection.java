package com.facilcomanda.ponoa.cash;

import java.math.BigDecimal;

public record PaymentSummaryProjection(String method, BigDecimal total) {}
