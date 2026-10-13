package com.facilcomanda.ponoa.billing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface InvoiceRepository extends CrudRepository<Invoice, Long> {

  Optional<Invoice> findByIdAndBranchId(Long id, Long branchId);

  Optional<Invoice> findByOrderId(Long orderId);

  List<Invoice> findAllByBranchId(Long branchId);
}
