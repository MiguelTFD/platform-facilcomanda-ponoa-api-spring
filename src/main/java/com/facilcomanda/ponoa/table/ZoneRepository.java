package com.facilcomanda.ponoa.table;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface ZoneRepository extends CrudRepository<RestaurantZone, Long> {

  @Query(
      "SELECT * FROM restaurant_zones "
          + "WHERE id = :id AND branch_id = :branchId AND is_active = TRUE")
  Optional<RestaurantZone> findByIdAndBranchId(
      @Param("id") Long id, @Param("branchId") Long branchId);

  @Query(
      "SELECT * FROM restaurant_zones "
          + "WHERE branch_id = :branchId AND is_active = TRUE ORDER BY name")
  List<RestaurantZone> findAllByBranchId(@Param("branchId") Long branchId);

  @Query(
      "SELECT * FROM restaurant_zones "
          + "WHERE branch_id = :branchId AND floor_id = :floorId AND is_active = TRUE ORDER BY name")
  List<RestaurantZone> findAllByBranchIdAndFloorId(
      @Param("branchId") Long branchId, @Param("floorId") Long floorId);

  @Query(
      "SELECT COUNT(*) > 0 FROM restaurant_zones "
          + "WHERE floor_id = :floorId AND LOWER(name) = LOWER(:name) AND is_active = TRUE")
  boolean existsByFloorIdAndName(
      @Param("floorId") Long floorId, @Param("name") String name);

  @Modifying
  @Query(
      """
      UPDATE restaurant_zones 
      SET floor_id = :floorId, 
          name = :name, 
          description = :description, 
          updated_at = NOW() 
      WHERE id = :id AND branch_id = :branchId AND is_active = TRUE
      """)
  int updateZone(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("floorId") Long floorId,
      @Param("name") String name,
      @Param("description") String description);

  @Modifying
  @Query(
      "UPDATE restaurant_zones SET is_active = FALSE, deleted_at = NOW(), updated_at = NOW() "
          + "WHERE id = :id AND branch_id = :branchId AND is_active = TRUE")
  int softDeleteZone(@Param("id") Long id, @Param("branchId") Long branchId);

  @Modifying
  @Query(
      "UPDATE restaurant_zones SET is_active = FALSE, deleted_at = NOW(), updated_at = NOW() "
          + "WHERE floor_id = :floorId AND branch_id = :branchId AND is_active = TRUE")
  int softDeleteZonesByFloor(@Param("floorId") Long floorId, @Param("branchId") Long branchId);
}
