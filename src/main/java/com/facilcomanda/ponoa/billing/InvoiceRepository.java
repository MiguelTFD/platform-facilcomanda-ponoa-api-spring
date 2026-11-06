package com.facilcomanda.ponoa.billing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface InvoiceRepository extends CrudRepository<Invoice, Long> {

  Optional<Invoice> findByIdAndBranchId(Long id, Long branchId);

  Optional<Invoice> findByOrderId(Long orderId);

  @Query("SELECT * FROM invoices WHERE branch_id = :branchId ORDER BY id DESC LIMIT 100")
  List<Invoice> findAllByBranchId(@Param("branchId") Long branchId);
}
