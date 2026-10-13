package com.facilcomanda.ponoa.inventory;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface StockRepository extends CrudRepository<StockLevel, Long> {

  @Modifying
  @Query(
      "UPDATE stock_levels SET quantity = quantity - :amount WHERE sku_id = :skuId AND quantity >="
          + " :amount")
  int deductStockAtomic(@Param("skuId") Long skuId, @Param("amount") int amount);

  @Modifying
  @Query("UPDATE stock_levels SET quantity = quantity + :amount WHERE sku_id = :skuId")
  int addStockAtomic(@Param("skuId") Long skuId, @Param("amount") int amount);

  @Modifying
  @Query("INSERT INTO stock_levels (sku_id, quantity) VALUES (:skuId, :quantity)")
  void initializeStock(@Param("skuId") Long skuId, @Param("quantity") int quantity);
}
