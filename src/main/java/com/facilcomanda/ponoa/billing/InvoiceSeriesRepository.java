package com.facilcomanda.ponoa.billing;

import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface InvoiceSeriesRepository extends CrudRepository<InvoiceSeries, Long> {

  @Query(
      "SELECT * FROM invoice_series "
          + "WHERE branch_id = :branchId AND invoice_type = :invoiceType FOR UPDATE")
  Optional<InvoiceSeries> findForUpdate(
      @Param("branchId") Long branchId, @Param("invoiceType") String invoiceType);

  @Modifying
  @Query(
      "INSERT INTO invoice_series (branch_id, invoice_type, series, current_number, updated_at) "
          + "VALUES (:branchId, :invoiceType, :series, :currentNumber, NOW()) "
          + "ON CONFLICT (branch_id, invoice_type, series) "
          + "DO NOTHING")
  void initSeries(
      @Param("branchId") Long branchId,
      @Param("invoiceType") String invoiceType,
      @Param("series") String series,
      @Param("currentNumber") Long currentNumber);

  @Modifying
  @Query(
      "UPDATE invoice_series SET current_number = :currentNumber, updated_at = NOW() "
          + "WHERE id = :id")
  void updateCurrentNumber(@Param("id") Long id, @Param("currentNumber") Long currentNumber);
}
