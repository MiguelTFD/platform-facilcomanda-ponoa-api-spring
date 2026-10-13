package com.facilcomanda.ponoa.printing;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("printer_devices")
public record PrinterDevice(
    @Id Long id,
    @Column("branch_id") Long branchId,
    String name,
    @Column("ip_address") String ipAddress,
    Integer port,
    @Column("connection_type") String connectionType,
    @Column("is_active") Boolean isActive,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt) {}
