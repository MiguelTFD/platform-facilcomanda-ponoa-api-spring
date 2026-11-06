package com.facilcomanda.ponoa.kitchen;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface KitchenTicketRepository extends CrudRepository<KitchenTicket, Long> {

  Optional<KitchenTicket> findByIdAndBranchId(Long id, Long branchId);

  Optional<KitchenTicket> findByOrderId(Long orderId);

  List<KitchenTicket> findAllByBranchId(Long branchId);

  @Query(
      "SELECT * FROM kitchen_tickets WHERE branch_id = :branchId AND status IN (:statuses) ORDER BY created_at ASC LIMIT 100")
  List<KitchenTicket> findAllByBranchIdAndStatusIn(
      @Param("branchId") Long branchId,
      @Param("statuses") Collection<KitchenTicketStatus> statuses);

  @Modifying
  @Query("UPDATE kitchen_tickets SET status = :status WHERE id = :id AND branch_id = :branchId")
  int updateStatusAtomic(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("status") String status);

  @Modifying
  @Query("UPDATE kitchen_tickets SET notes = :notes, status = :status, updated_at = NOW() WHERE id = :id AND branch_id = :branchId")
  int updateNotesAndStatus(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("notes") String notes,
      @Param("status") String status);

  @Modifying
  @Query(
      "UPDATE kitchen_tickets SET status = 'CANCELLED', notes = :notes, updated_at = NOW() "
          + "WHERE branch_id = :branchId AND order_id = :orderId "
          + "AND status IN ('PENDING', 'IN_PREPARATION', 'READY')")
  int cancelTicketsByOrder(
      @Param("branchId") Long branchId,
      @Param("orderId") Long orderId,
      @Param("notes") String notes);
}
