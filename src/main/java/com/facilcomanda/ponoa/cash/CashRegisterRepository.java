package com.facilcomanda.ponoa.cash;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface CashRegisterRepository extends CrudRepository<CashRegister, Long> {

  List<CashRegister> findAllByBranchIdAndIsActiveTrue(Long branchId);

  Optional<CashRegister> findByIdAndBranchId(Long id, Long branchId);

  boolean existsByBranchIdAndName(Long branchId, String name);
}
