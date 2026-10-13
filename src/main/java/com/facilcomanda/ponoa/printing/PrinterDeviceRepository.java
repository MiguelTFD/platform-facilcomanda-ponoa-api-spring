package com.facilcomanda.ponoa.printing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface PrinterDeviceRepository extends CrudRepository<PrinterDevice, Long> {

  Optional<PrinterDevice> findByIdAndBranchId(Long id, Long branchId);

  List<PrinterDevice> findAllByBranchId(Long branchId);

  List<PrinterDevice> findAllByBranchIdAndIsActiveTrue(Long branchId);

  Optional<PrinterDevice> findByBranchIdAndTerminalCodeAndIsActiveTrue(
      Long branchId, String terminalCode);

  boolean existsByBranchIdAndName(Long branchId, String name);
}
