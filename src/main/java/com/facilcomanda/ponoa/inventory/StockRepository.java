package com.facilcomanda.ponoa.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface StockRepository extends CrudRepository<StockLevel, Long> {

  Optional<StockLevel> findByBranchIdAndSkuId(Long branchId, Long skuId);

  boolean existsByBranchIdAndSkuId(Long branchId, Long skuId);

  List<StockLevel> findAllByBranchId(Long branchId);

  @Modifying
  @Query(
      "UPDATE stock_levels SET quantity = quantity - :amount "
          + "WHERE branch_id = :branchId AND sku_id = :skuId AND quantity >= :amount")
  int deductStockAtomic(
      @Param("branchId") Long branchId,
      @Param("skuId") Long skuId,
      @Param("amount") int amount);

  @Modifying
  @Query(
      "UPDATE stock_levels SET quantity = quantity + :amount "
          + "WHERE branch_id = :branchId AND sku_id = :skuId")
  int addStockAtomic(
      @Param("branchId") Long branchId,
      @Param("skuId") Long skuId,
      @Param("amount") int amount);

  @Modifying
  @Query(
      "INSERT INTO stock_levels (branch_id, sku_id, quantity) "
          + "VALUES (:branchId, :skuId, :quantity) "
          + "ON CONFLICT (branch_id, sku_id) DO UPDATE SET quantity = EXCLUDED.quantity")
  void upsertStock(
      @Param("branchId") Long branchId,
      @Param("skuId") Long skuId,
      @Param("quantity") int quantity);
}
