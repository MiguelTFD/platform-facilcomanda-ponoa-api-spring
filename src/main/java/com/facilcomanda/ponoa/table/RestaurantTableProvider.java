package com.facilcomanda.ponoa.table;

import java.util.Optional;

public interface RestaurantTableProvider {

  /**
   * Validates and returns table and floor information for order placement.
   */
  Optional<RestaurantTableView> findTableByIdAndBranch(Long tableId, Long branchId);

  /**
   * Updates table state (e.g. to OCCUPIED when a dine-in order is placed, or AVAILABLE when closed).
   */
  void updateTableState(Long tableId, Long branchId, TableState newState);

  /**
   * Attempts to atomically occupy a table if it is currently AVAILABLE.
   * Returns true if successfully occupied, or false if already occupied or not available.
   */
  boolean occupyTableAtomically(Long tableId, Long branchId);
}
