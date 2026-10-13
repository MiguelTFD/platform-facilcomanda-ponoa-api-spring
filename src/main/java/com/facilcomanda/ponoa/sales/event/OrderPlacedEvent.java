package com.facilcomanda.ponoa.sales.event;

import java.math.BigDecimal;

public record OrderPlacedEvent(Long orderId, String customerEmail, BigDecimal totalAmount) {}
