package com.facilcomanda.ponoa.cash;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("cash_shifts")
public record CashShift(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("cash_register_id") Long cashRegisterId,
    @Column("cashier_user_id") UUID cashierUserId,
    @Column("cashier_name") String cashierName,
    String status,
    @Column("opening_amount") BigDecimal openingAmount,
    @Column("opened_at") OffsetDateTime openedAt,
    @Column("closed_at") OffsetDateTime closedAt,
    @Column("closing_cash_counted") BigDecimal closingCashCounted,
    @Column("expected_cash_amount") BigDecimal expectedCashAmount,
    @Column("cash_difference") BigDecimal cashDifference,
    @Column("total_sales_amount") BigDecimal totalSalesAmount,
    String notes,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
