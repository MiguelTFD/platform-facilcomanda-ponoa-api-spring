package com.facilcomanda.ponoa.table;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface FloorRepository extends CrudRepository<RestaurantFloor, Long> {

  @Query(
      "SELECT * FROM restaurant_floors "
          + "WHERE id = :id AND branch_id = :branchId AND is_active = TRUE")
  Optional<RestaurantFloor> findByIdAndBranchId(
      @Param("id") Long id, @Param("branchId") Long branchId);

  @Query(
      "SELECT * FROM restaurant_floors "
          + "WHERE branch_id = :branchId AND is_active = TRUE ORDER BY name")
  List<RestaurantFloor> findAllByBranchId(@Param("branchId") Long branchId);

  @Query(
      "SELECT COUNT(*) > 0 FROM restaurant_floors "
          + "WHERE branch_id = :branchId AND name = :name AND is_active = TRUE")
  boolean existsByBranchIdAndName(
      @Param("branchId") Long branchId, @Param("name") String name);

  @Modifying
  @Query(
      "UPDATE restaurant_floors SET is_active = FALSE, deleted_at = NOW(), updated_at = NOW() "
          + "WHERE id = :id AND branch_id = :branchId AND is_active = TRUE")
  int softDeleteFloor(@Param("id") Long id, @Param("branchId") Long branchId);

  @Modifying
  @Query(
      """
      UPDATE restaurant_floors 
      SET name = :name, 
          description = :description, 
          updated_at = NOW() 
      WHERE id = :id AND branch_id = :branchId AND is_active = TRUE
      """)
  int updateFloor(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("name") String name,
      @Param("description") String description);
}
