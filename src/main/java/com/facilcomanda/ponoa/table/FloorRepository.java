package com.facilcomanda.ponoa.table;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface FloorRepository extends CrudRepository<RestaurantFloor, Long> {

  Optional<RestaurantFloor> findByIdAndBranchId(Long id, Long branchId);

  List<RestaurantFloor> findAllByBranchId(Long branchId);

  boolean existsByBranchIdAndName(Long branchId, String name);
}
