package com.facilcomanda.ponoa.sales;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface OrderRepository extends CrudRepository<CustomerOrder, Long> {

  List<CustomerOrder> findAllByBranchId(Long branchId);

  Optional<CustomerOrder> findByIdAndBranchId(Long id, Long branchId);
}
