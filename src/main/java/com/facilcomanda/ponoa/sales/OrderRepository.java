package com.facilcomanda.ponoa.sales;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface OrderRepository extends CrudRepository<CustomerOrder, Long> {

  List<CustomerOrder> findAllByBranchId(Long branchId);

  Optional<CustomerOrder> findByIdAndBranchId(Long id, Long branchId);

  @Modifying
  @Query("UPDATE customer_orders SET status = :status WHERE id = :id AND branch_id = :branchId")
  int updateStatus(@Param("id") Long id, @Param("branchId") Long branchId, @Param("status") String status);
}
