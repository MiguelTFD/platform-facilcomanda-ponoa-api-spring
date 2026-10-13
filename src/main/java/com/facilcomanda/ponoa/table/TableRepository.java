package com.facilcomanda.ponoa.table;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface TableRepository extends CrudRepository<RestaurantTable, Long> {

  Optional<RestaurantTable> findByIdAndBranchId(Long id, Long branchId);

  List<RestaurantTable> findAllByBranchId(Long branchId);

  List<RestaurantTable> findAllByBranchIdAndFloorId(Long branchId, Long floorId);

  boolean existsByFloorIdAndName(Long floorId, String name);

  @Modifying
  @Query(
      "UPDATE restaurant_tables SET state = :state, updated_at = NOW() "
          + "WHERE id = :id AND branch_id = :branchId")
  int updateTableState(
      @Param("id") Long id, @Param("branchId") Long branchId, @Param("state") String state);

  @Modifying
  @Query(
      "UPDATE restaurant_tables SET state = 'OCCUPIED', updated_at = NOW() "
          + "WHERE id = :id AND branch_id = :branchId AND state = 'AVAILABLE'")
  int occupyTableAtomically(@Param("id") Long id, @Param("branchId") Long branchId);

  @Query(
      "SELECT t.id, t.branch_id, t.floor_id, t.name as table_name, f.name as floor_name, t.state "
          + "FROM restaurant_tables t "
          + "JOIN restaurant_floors f ON t.floor_id = f.id "
          + "WHERE t.id = :id AND t.branch_id = :branchId")
  Optional<RestaurantTableView> findTableView(
      @Param("id") Long id, @Param("branchId") Long branchId);
}
