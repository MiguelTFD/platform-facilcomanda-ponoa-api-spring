package com.facilcomanda.ponoa.cash;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface CashRegisterRepository extends CrudRepository<CashRegister, Long> {

  List<CashRegister> findAllByBranchIdAndIsActiveTrue(Long branchId);

  Optional<CashRegister> findByIdAndBranchId(Long id, Long branchId);

  boolean existsByBranchIdAndName(Long branchId, String name);

  @Modifying
  @Query(
      """
      UPDATE cash_registers 
      SET name = :name, 
          description = :description, 
          is_active = :isActive, 
          updated_at = NOW() 
      WHERE id = :id AND branch_id = :branchId
      """)
  int updateRegisterDetails(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("name") String name,
      @Param("description") String description,
      @Param("isActive") Boolean isActive);

  @Modifying
  @Query("UPDATE cash_registers SET is_active = FALSE, updated_at = NOW() WHERE id = :id AND branch_id = :branchId")
  int softDeleteRegister(@Param("id") Long id, @Param("branchId") Long branchId);
}
