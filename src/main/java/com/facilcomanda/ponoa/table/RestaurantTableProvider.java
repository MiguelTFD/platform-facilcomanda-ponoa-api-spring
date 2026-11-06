package com.facilcomanda.ponoa.table;

import java.util.Optional;

public interface RestaurantTableProvider {

  /**
   * Validates and returns table and floor information for order placement.
   */
  Optional<RestaurantTableView> findTableByIdAndBranch(Long tableId, Long branchId);

  /**
   * Acquires a pessimistic row-level lock (SELECT ... FOR UPDATE) on the table and returns its view
   * to serialize concurrent order creation on the same table.
   */
  default Optional<RestaurantTableView> lockTableByIdAndBranch(Long tableId, Long branchId) {
    return findTableByIdAndBranch(tableId, branchId);
  }

  /**
   * Updates table state (e.g. to OCCUPIED when a dine-in order is placed, or AVAILABLE when closed).
   */
  void updateTableState(Long tableId, Long branchId, TableState newState);

  /**
   * Attempts to atomically occupy a table if it is currently AVAILABLE.
   * Returns true if successfully occupied, or false if already occupied or not available.
   */
  boolean occupyTableAtomically(Long tableId, Long branchId);

  /**
   * Batch resolves table names for a collection of table IDs in a branch.
   */
  default java.util.Map<Long, String> getTableNames(
      java.util.Collection<Long> tableIds, Long branchId) {
    if (tableIds == null || tableIds.isEmpty()) {
      return java.util.Map.of();
    }
    return tableIds.stream()
        .distinct()
        .map(id -> findTableByIdAndBranch(id, branchId))
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .collect(
            java.util.stream.Collectors.toMap(
                RestaurantTableView::id, RestaurantTableView::tableName, (a, b) -> a));
  }
}
