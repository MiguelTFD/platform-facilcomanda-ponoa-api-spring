package com.facilcomanda.ponoa.printing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface PrintJobRepository extends CrudRepository<PrintJob, Long> {

  Optional<PrintJob> findByIdAndBranchId(Long id, Long branchId);

  List<PrintJob> findAllByBranchIdAndStatus(Long branchId, PrintJobStatus status);

  @Modifying
  @Query(
      "UPDATE print_jobs SET status = :status, error_message = :errorMessage, retry_count = retry_count + 1 "
          + "WHERE id = :id AND branch_id = :branchId")
  int updateJobStatus(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("status") String status,
      @Param("errorMessage") String errorMessage);
}
