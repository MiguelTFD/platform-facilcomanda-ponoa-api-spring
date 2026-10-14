package com.facilcomanda.ponoa.cash;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface CashShiftRepository extends CrudRepository<CashShift, Long> {

  Optional<CashShift> findByBranchIdAndCashierUserIdAndStatus(
      Long branchId, UUID cashierUserId, String status);

  Optional<CashShift> findByCashRegisterIdAndStatus(Long cashRegisterId, String status);

  Optional<CashShift> findByIdAndBranchId(Long id, Long branchId);

  Optional<CashShift> findFirstByBranchIdAndStatusOrderByOpenedAtDesc(Long branchId, String status);

  List<CashShift> findAllByBranchIdOrderByOpenedAtDesc(Long branchId);

  @Query(
      "SELECT COALESCE(SUM(amount), 0) FROM cash_movements "
          + "WHERE cash_shift_id = :shiftId AND type = :type")
  BigDecimal sumMovementsByType(
      @Param("shiftId") Long shiftId, @Param("type") String type);

  @Query(
      "SELECT ip.method as method, COALESCE(SUM(ip.amount), 0) as total "
          + "FROM invoice_payments ip "
          + "JOIN invoices i ON ip.invoice_id = i.id "
          + "WHERE i.cash_shift_id = :shiftId "
          + "GROUP BY ip.method")
  List<PaymentSummaryProjection> getPaymentsSummaryByShiftId(@Param("shiftId") Long shiftId);

  @Query(
      "SELECT COALESCE(SUM(i.change_amount), 0) FROM invoices i "
          + "WHERE i.cash_shift_id = :shiftId")
  BigDecimal sumChangeGivenByShiftId(@Param("shiftId") Long shiftId);

  @Query(
      "SELECT COALESCE(SUM(i.order_total), 0) FROM invoices i "
          + "WHERE i.cash_shift_id = :shiftId")
  BigDecimal sumOrderTotalsByShiftId(@Param("shiftId") Long shiftId);

  @Query(
      "SELECT i.invoice_type as type, COUNT(*) as count "
          + "FROM invoices i "
          + "WHERE i.cash_shift_id = :shiftId "
          + "GROUP BY i.invoice_type")
  List<InvoiceCountProjection> getInvoiceCountsByShiftId(@Param("shiftId") Long shiftId);

  @Query("SELECT COUNT(*) FROM invoices WHERE cash_shift_id = :shiftId")
  long countInvoicesByShiftId(@Param("shiftId") Long shiftId);
}

