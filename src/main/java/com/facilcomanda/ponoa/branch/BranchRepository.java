package com.facilcomanda.ponoa.branch;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface BranchRepository extends CrudRepository<Branch, Long> {

  Optional<Branch> findByCode(String code);

  boolean existsByCode(String code);

  boolean existsByName(String name);

  List<Branch> findAllByIsActiveTrue();

  @Modifying
  @Query(
      """
      UPDATE branches 
      SET name = :name, 
          address = :address, 
          phone_number = :phoneNumber, 
          is_active = :isActive, 
          updated_at = NOW() 
      WHERE id = :id
      """)
  int updateBranchDetails(
      @Param("id") Long id,
      @Param("name") String name,
      @Param("address") String address,
      @Param("phoneNumber") String phoneNumber,
      @Param("isActive") Boolean isActive);
}
