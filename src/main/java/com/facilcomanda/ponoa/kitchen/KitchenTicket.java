package com.facilcomanda.ponoa.kitchen;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("kitchen_tickets")
public record KitchenTicket(
    @Id Long id,
    @Column("branch_id") Long branchId,
    @Column("order_id") Long orderId,
    KitchenTicketStatus status,
    String notes,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
