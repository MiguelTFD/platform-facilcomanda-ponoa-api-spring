package com.facilcomanda.ponoa.billing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("invoices")
public record Invoice(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("cash_shift_id") Long cashShiftId,
    @Column("order_id") Long orderId,
    @Column("customer_id") Long customerId,
    @Column("invoice_number") String invoiceNumber,
    @Column("invoice_type") String invoiceType,
    @Column("order_total") BigDecimal orderTotal,
    @Column("amount_paid") BigDecimal amountPaid,
    @Column("change_amount") BigDecimal changeAmount,
    @Column("cashier_user_id") UUID cashierUserId,
    @Column("cashier_email") String cashierEmail,
    @Column("restaurant_table_id") Long restaurantTableId,
    @Column("restaurant_table_name") String restaurantTableName,
    String notes,
    @Column("paid_at") OffsetDateTime paidAt,
    @Column("created_at") OffsetDateTime createdAt,
    @MappedCollection(idColumn = "invoice_id") Set<InvoicePayment> payments) {}
