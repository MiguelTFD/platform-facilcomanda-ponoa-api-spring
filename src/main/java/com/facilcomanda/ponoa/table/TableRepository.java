package com.facilcomanda.ponoa.table;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface TableRepository extends CrudRepository<RestaurantTable, Long> {

  @Query(
      "SELECT * FROM restaurant_tables "
          + "WHERE id = :id AND branch_id = :branchId AND is_active = TRUE")
  Optional<RestaurantTable> findByIdAndBranchId(
      @Param("id") Long id, @Param("branchId") Long branchId);

  @Query(
      "SELECT * FROM restaurant_tables "
          + "WHERE branch_id = :branchId AND is_active = TRUE ORDER BY name")
  List<RestaurantTable> findAllByBranchId(@Param("branchId") Long branchId);

  @Query(
      "SELECT * FROM restaurant_tables "
          + "WHERE branch_id = :branchId AND floor_id = :floorId AND is_active = TRUE ORDER BY name")
  List<RestaurantTable> findAllByBranchIdAndFloorId(
      @Param("branchId") Long branchId, @Param("floorId") Long floorId);

  @Query(
      "SELECT * FROM restaurant_tables "
          + "WHERE branch_id = :branchId AND zone_id = :zoneId AND is_active = TRUE ORDER BY name")
  List<RestaurantTable> findAllByBranchIdAndZoneId(
      @Param("branchId") Long branchId, @Param("zoneId") Long zoneId);

  @Query(
      "SELECT * FROM restaurant_tables "
          + "WHERE branch_id = :branchId AND floor_id = :floorId AND zone_id = :zoneId AND is_active = TRUE ORDER BY name")
  List<RestaurantTable> findAllByBranchIdAndFloorIdAndZoneId(
      @Param("branchId") Long branchId,
      @Param("floorId") Long floorId,
      @Param("zoneId") Long zoneId);

  @Query(
      "SELECT COUNT(*) > 0 FROM restaurant_tables "
          + "WHERE floor_id = :floorId AND name = :name AND is_active = TRUE")
  boolean existsByFloorIdAndName(
      @Param("floorId") Long floorId, @Param("name") String name);

  @Query(
      "SELECT COUNT(*) FROM restaurant_tables "
          + "WHERE branch_id = :branchId AND floor_id = :floorId AND is_active = TRUE")
  long countByBranchIdAndFloorIdAndIsActiveTrue(
      @Param("branchId") Long branchId, @Param("floorId") Long floorId);

  @Modifying
  @Query(
      "UPDATE restaurant_tables SET state = :state, updated_at = NOW() "
          + "WHERE id = :id AND branch_id = :branchId AND is_active = TRUE")
  int updateTableState(
      @Param("id") Long id, @Param("branchId") Long branchId, @Param("state") String state);

  @Modifying
  @Query(
      "UPDATE restaurant_tables SET state = 'OCCUPIED', updated_at = NOW() "
          + "WHERE id = :id AND branch_id = :branchId AND state = 'AVAILABLE' AND is_active = TRUE")
  int occupyTableAtomically(@Param("id") Long id, @Param("branchId") Long branchId);

  @Query(
      "SELECT t.id, t.branch_id, t.floor_id, t.zone_id, t.name as table_name, "
          + "f.name as floor_name, z.name as zone_name, t.state "
          + "FROM restaurant_tables t "
          + "JOIN restaurant_floors f ON t.floor_id = f.id "
          + "LEFT JOIN restaurant_zones z ON t.zone_id = z.id AND z.is_active = TRUE "
          + "WHERE t.id = :id AND t.branch_id = :branchId AND t.is_active = TRUE AND f.is_active = TRUE")
  Optional<RestaurantTableView> findTableView(
      @Param("id") Long id, @Param("branchId") Long branchId);

  @Query(
      "SELECT t.id, t.branch_id, t.floor_id, t.zone_id, t.name as table_name, "
          + "f.name as floor_name, z.name as zone_name, t.state "
          + "FROM restaurant_tables t "
          + "JOIN restaurant_floors f ON t.floor_id = f.id "
          + "LEFT JOIN restaurant_zones z ON t.zone_id = z.id AND z.is_active = TRUE "
          + "WHERE t.id = :id AND t.branch_id = :branchId AND t.is_active = TRUE AND f.is_active = TRUE "
          + "FOR UPDATE OF t")
  Optional<RestaurantTableView> findTableViewForUpdate(
      @Param("id") Long id, @Param("branchId") Long branchId);

  @Query(
      "SELECT t.id, t.branch_id, t.floor_id, t.zone_id, t.name as table_name, "
          + "f.name as floor_name, z.name as zone_name, t.state "
          + "FROM restaurant_tables t "
          + "JOIN restaurant_floors f ON t.floor_id = f.id "
          + "LEFT JOIN restaurant_zones z ON t.zone_id = z.id AND z.is_active = TRUE "
          + "WHERE t.id IN (:ids) AND t.branch_id = :branchId AND t.is_active = TRUE AND f.is_active = TRUE")
  java.util.List<RestaurantTableView> findTableViews(
      @Param("ids") java.util.Collection<Long> ids, @Param("branchId") Long branchId);

  @Modifying
  @Query(
      "UPDATE restaurant_tables SET is_active = FALSE, deleted_at = NOW(), updated_at = NOW() "
          + "WHERE id = :id AND branch_id = :branchId AND is_active = TRUE")
  int softDeleteTable(@Param("id") Long id, @Param("branchId") Long branchId);

  @Modifying
  @Query(
      "UPDATE restaurant_tables SET is_active = FALSE, deleted_at = NOW(), updated_at = NOW() "
          + "WHERE floor_id = :floorId AND branch_id = :branchId AND is_active = TRUE")
  int softDeleteTablesByFloor(@Param("floorId") Long floorId, @Param("branchId") Long branchId);

  @Modifying
  @Query(
      "UPDATE restaurant_tables SET is_active = FALSE, deleted_at = NOW(), updated_at = NOW() "
          + "WHERE zone_id = :zoneId AND branch_id = :branchId AND is_active = TRUE")
  int softDeleteTablesByZone(@Param("zoneId") Long zoneId, @Param("branchId") Long branchId);

  @Modifying
  @Query(
      "UPDATE restaurant_tables SET floor_id = :floorId, updated_at = NOW() "
          + "WHERE zone_id = :zoneId AND branch_id = :branchId AND is_active = TRUE")
  int updateFloorByZone(
      @Param("zoneId") Long zoneId,
      @Param("branchId") Long branchId,
      @Param("floorId") Long floorId);

  @Modifying
  @Query(
      """
      UPDATE restaurant_tables 
      SET floor_id = :floorId, 
          name = :name, 
          description = :description, 
          chairs = :chairs, 
          updated_at = NOW() 
      WHERE id = :id AND branch_id = :branchId AND is_active = TRUE
      """)
  int updateTableDetails(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("floorId") Long floorId,
      @Param("name") String name,
      @Param("description") String description,
      @Param("chairs") Integer chairs);

  @Modifying
  @Query(
      """
      UPDATE restaurant_tables 
      SET floor_id = :floorId, 
          zone_id = :zoneId, 
          name = :name, 
          description = :description, 
          chairs = :chairs, 
          updated_at = NOW() 
      WHERE id = :id AND branch_id = :branchId AND is_active = TRUE
      """)
  int updateTableDetailsWithZone(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("floorId") Long floorId,
      @Param("zoneId") Long zoneId,
      @Param("name") String name,
      @Param("description") String description,
      @Param("chairs") Integer chairs);
}
