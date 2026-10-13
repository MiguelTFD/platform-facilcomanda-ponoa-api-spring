package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("customer_orders")
public record CustomerOrder(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("app_user_id") UUID appUserId,
    @Column("idempotency_key") String idempotencyKey,
    BigDecimal totalAmount,
    String status,
    @MappedCollection(idColumn = "order_id") Set<OrderItem> items) {}
