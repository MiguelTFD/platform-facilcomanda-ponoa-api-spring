package com.facilcomanda.ponoa.cash;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("cash_movements")
public record CashMovement(
    @Id Long id,
    @Column("cash_shift_id") Long cashShiftId,
    String type,
    BigDecimal amount,
    String reason,
    @Column("performed_by_user_id") UUID performedByUserId,
    @Column("created_at") OffsetDateTime createdAt) {}
