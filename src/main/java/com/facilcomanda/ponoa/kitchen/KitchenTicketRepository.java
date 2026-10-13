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

  List<KitchenTicket> findAllByBranchIdAndStatusIn(
      Long branchId, Collection<KitchenTicketStatus> statuses);

  @Modifying
  @Query("UPDATE kitchen_tickets SET status = :status WHERE id = :id AND branch_id = :branchId")
  int updateStatusAtomic(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("status") String status);
}
