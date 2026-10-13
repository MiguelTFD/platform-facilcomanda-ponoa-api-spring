package com.facilcomanda.ponoa.printing;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

interface PrinterRouteRepository extends CrudRepository<PrinterRoute, Long> {

  List<PrinterRoute> findAllByBranchIdAndDocumentType(
      Long branchId, PrintDocumentType documentType);

  List<PrinterRoute> findAllByBranchIdAndPrinterId(Long branchId, Long printerId);

  boolean existsByBranchIdAndPrinterIdAndDocumentType(
      Long branchId, Long printerId, PrintDocumentType documentType);
}
