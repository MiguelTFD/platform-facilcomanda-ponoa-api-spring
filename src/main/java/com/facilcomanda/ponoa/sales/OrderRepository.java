package com.facilcomanda.ponoa.sales;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface OrderRepository extends CrudRepository<CustomerOrder, Long> {

  List<CustomerOrder> findAllByBranchId(Long branchId);

  @Query("SELECT * FROM customer_orders WHERE branch_id = :branchId ORDER BY id DESC LIMIT 100")
  List<CustomerOrder> findAllByBranchIdOrderByIdDesc(@Param("branchId") Long branchId);

  @Query(
      "SELECT * FROM customer_orders WHERE branch_id = :branchId AND status = :status ORDER BY id DESC LIMIT 100")
  List<CustomerOrder> findAllByBranchIdAndStatusOrderByIdDesc(
      @Param("branchId") Long branchId, @Param("status") String status);

  Optional<CustomerOrder> findByIdAndBranchId(Long id, Long branchId);

  @Query(
      "SELECT * FROM customer_orders "
          + "WHERE branch_id = :branchId AND idempotency_key = :idempotencyKey LIMIT 1")
  Optional<CustomerOrder> findByBranchIdAndIdempotencyKey(
      @Param("branchId") Long branchId, @Param("idempotencyKey") String idempotencyKey);

  @Query(
      "SELECT created_at FROM customer_orders "
          + "WHERE branch_id = :branchId AND table_id = :tableId AND status = 'PENDING' "
          + "ORDER BY created_at DESC LIMIT 1")
  Optional<OffsetDateTime> findLatestPendingOrderCreatedAtByTable(
      @Param("branchId") Long branchId, @Param("tableId") Long tableId);

  @Query(
      "SELECT COUNT(*) FROM customer_orders "
          + "WHERE branch_id = :branchId AND table_id = :tableId AND status NOT IN ('PAID', 'CANCELLED')")
  long countActiveOrdersByTableId(
      @Param("branchId") Long branchId, @Param("tableId") Long tableId);

  @Query(
      "SELECT * FROM customer_orders "
          + "WHERE branch_id = :branchId AND table_id = :tableId AND status NOT IN ('PAID', 'CANCELLED') "
          + "ORDER BY id DESC")
  List<CustomerOrder> findActiveOrdersByTableId(
      @Param("branchId") Long branchId, @Param("tableId") Long tableId);

  @Modifying
  @Query("UPDATE customer_orders SET status = :status, updated_at = NOW() WHERE id = :id AND branch_id = :branchId")
  int updateStatus(@Param("id") Long id, @Param("branchId") Long branchId, @Param("status") String status);

  @Modifying
  @Query(
      "UPDATE customer_orders SET status = 'CANCELLED', cancelled_by = :cancelledBy, "
          + "cancellation_reason = :reason, cancelled_at = NOW(), updated_at = NOW() "
          + "WHERE id = :id AND branch_id = :branchId")
  int cancelOrder(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("cancelledBy") UUID cancelledBy,
      @Param("reason") String reason);

  @Query(
      "SELECT * FROM customer_orders "
          + "WHERE branch_id = :branchId AND table_id = :tableId AND status NOT IN ('PAID', 'CANCELLED') "
          + "ORDER BY id DESC LIMIT 1")
  Optional<CustomerOrder> findActiveOrderByTableId(
      @Param("branchId") Long branchId, @Param("tableId") Long tableId);
}
