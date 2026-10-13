package com.facilcomanda.ponoa.catalog;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface BranchProductRepository extends CrudRepository<BranchProduct, Long> {

  Optional<BranchProduct> findByBranchIdAndSkuId(Long branchId, Long skuId);

  List<BranchProduct> findAllByBranchId(Long branchId);

  @Query("SELECT * FROM branch_products WHERE branch_id = :branchId AND sku_id IN (:skuIds)")
  List<BranchProduct> findByBranchIdAndSkuIds(
      @Param("branchId") Long branchId, @Param("skuIds") Collection<Long> skuIds);

  @Query("SELECT id as sku_id, current_price FROM sku_presentations WHERE id IN (:skuIds)")
  List<SkuBasePriceView> findBasePricesBySkuIds(@Param("skuIds") Collection<Long> skuIds);

  @Query(
      "SELECT sp.id as sku_id, COALESCE(p.production_area, 'COCINA') as production_area "
          + "FROM sku_presentations sp "
          + "JOIN products p ON sp.product_id = p.id "
          + "WHERE sp.id IN (:skuIds)")
  List<SkuProductionAreaView> findProductionAreasBySkuIds(
      @Param("skuIds") Collection<Long> skuIds);

  @Modifying
  @Query(
      "INSERT INTO branch_products (branch_id, sku_id, price, is_available) "
          + "VALUES (:branchId, :skuId, :price, :isAvailable) "
          + "ON CONFLICT (branch_id, sku_id) "
          + "DO UPDATE SET price = EXCLUDED.price, is_available = EXCLUDED.is_available")
  void upsertBranchProduct(
      @Param("branchId") Long branchId,
      @Param("skuId") Long skuId,
      @Param("price") BigDecimal price,
      @Param("isAvailable") boolean isAvailable);
}

record SkuBasePriceView(Long skuId, BigDecimal currentPrice) {}

record SkuProductionAreaView(Long skuId, String productionArea) {}
