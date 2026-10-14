package com.facilcomanda.ponoa.cash;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface CashMovementRepository extends CrudRepository<CashMovement, Long> {

  List<CashMovement> findAllByCashShiftIdOrderByCreatedAtDesc(Long cashShiftId);

  @Query(
      "SELECT cm.id as id, cs.branch_id as branch_id, cm.cash_shift_id as shift_id, "
          + "cm.amount as amount, cm.reason as reason, cm.performed_by_user_id as registered_by_user_id, "
          + "COALESCE(u.first_name || ' ' || u.last_name, u.email, 'Usuario') as registered_by_name, "
          + "cm.created_at as created_at "
          + "FROM cash_movements cm "
          + "JOIN cash_shifts cs ON cm.cash_shift_id = cs.id "
          + "LEFT JOIN app_users u ON cm.performed_by_user_id = u.id "
          + "WHERE cm.type = 'EXPENSE' "
          + "  AND (:branchId IS NULL OR cs.branch_id = :branchId) "
          + "  AND cm.created_at >= :from AND cm.created_at <= :to "
          + "ORDER BY cm.created_at DESC")
  List<ExpenseView> findExpenses(
      @Param("branchId") Long branchId,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);
}

record ExpenseView(
    Long id,
    Long branchId,
    Long shiftId,
    BigDecimal amount,
    String reason,
    UUID registeredByUserId,
    String registeredByName,
    OffsetDateTime createdAt) {}

