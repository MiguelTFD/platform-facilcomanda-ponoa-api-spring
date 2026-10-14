package com.facilcomanda.ponoa.sales;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

  @Modifying
  @Query(
      "UPDATE customer_orders SET status = 'CANCELLED', cancelled_by = :cancelledBy, "
          + "cancellation_reason = :reason, cancelled_at = NOW() "
          + "WHERE id = :id AND branch_id = :branchId")
  int cancelOrder(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("cancelledBy") UUID cancelledBy,
      @Param("reason") String reason);

  @Query(
      "SELECT * FROM customer_orders "
          + "WHERE branch_id = :branchId AND table_id = :tableId AND status = 'PENDING' "
          + "ORDER BY id DESC LIMIT 1")
  Optional<CustomerOrder> findActiveOrderByTableId(
      @Param("branchId") Long branchId, @Param("tableId") Long tableId);
}
