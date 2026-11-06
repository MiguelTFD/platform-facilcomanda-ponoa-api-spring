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

  @Query("SELECT id as sku_id, presentation_name FROM sku_presentations WHERE id IN (:skuIds)")
  List<SkuNameView> findPresentationNamesBySkuIds(@Param("skuIds") Collection<Long> skuIds);

  @Query(
      "SELECT id as sku_id, COALESCE(requires_stock, TRUE) as requires_stock "
          + "FROM sku_presentations WHERE id IN (:skuIds)")
  List<SkuRequiresStockView> findRequiresStockBySkuIds(@Param("skuIds") Collection<Long> skuIds);

  @Query("SELECT id as sku_id, product_id FROM sku_presentations WHERE id IN (:skuIds)")
  List<SkuProductIdView> findProductIdsBySkuIds(@Param("skuIds") Collection<Long> skuIds);

  @Query(
      "SELECT bp.id as id, :branchId as branch_id, sp.id as sku_id, sp.sku_code as sku_code, "
          + "p.name as product_name, sp.presentation_name as presentation_name, "
          + "COALESCE(bp.price, sp.current_price) as price, "
          + "COALESCE(bp.is_available, TRUE) as is_available, "
          + "COALESCE(p.production_area, 'COCINA') as production_area, "
          + "COALESCE(sp.requires_stock, TRUE) as requires_stock "
          + "FROM sku_presentations sp "
          + "JOIN products p ON sp.product_id = p.id "
          + "LEFT JOIN branch_products bp ON bp.sku_id = sp.id AND bp.branch_id = :branchId "
          + "WHERE p.is_active = TRUE AND sp.is_active = TRUE "
          + "ORDER BY p.name, sp.presentation_name")
  List<BranchProductDetailedView> findAllDetailedByBranchId(@Param("branchId") Long branchId);

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

record SkuNameView(Long skuId, String presentationName) {}

record SkuRequiresStockView(Long skuId, Boolean requiresStock) {}

record SkuProductIdView(Long skuId, Long productId) {}

record BranchProductDetailedView(
    Long id,
    Long branchId,
    Long skuId,
    String skuCode,
    String productName,
    String presentationName,
    BigDecimal price,
    Boolean isAvailable,
    String productionArea,
    Boolean requiresStock) {

  @org.springframework.data.annotation.PersistenceCreator
  BranchProductDetailedView {}

  BranchProductDetailedView(
      Long id,
      Long branchId,
      Long skuId,
      String skuCode,
      String productName,
      String presentationName,
      BigDecimal price,
      Boolean isAvailable) {
    this(
        id,
        branchId,
        skuId,
        skuCode,
        productName,
        presentationName,
        price,
        isAvailable,
        "COCINA",
        true);
  }
}
