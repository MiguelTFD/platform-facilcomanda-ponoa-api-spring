package com.facilcomanda.ponoa.cash;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

interface CashMovementRepository extends CrudRepository<CashMovement, Long> {

  List<CashMovement> findAllByCashShiftIdOrderByCreatedAtDesc(Long cashShiftId);
}
