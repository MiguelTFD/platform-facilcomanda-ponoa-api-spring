package com.facilcomanda.ponoa.catalog;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface SkuPresentationRepository extends CrudRepository<SkuPresentation, Long> {

  @Query("SELECT * FROM sku_presentations WHERE product_id = :productId AND is_active = TRUE ORDER BY id")
  List<SkuPresentation> findByProductIdActive(@Param("productId") Long productId);

  @Query("SELECT * FROM sku_presentations WHERE id = :id AND product_id = :productId AND is_active = TRUE")
  Optional<SkuPresentation> findByIdAndProductIdActive(
      @Param("id") Long id, @Param("productId") Long productId);

  @Query("SELECT COUNT(*) > 0 FROM sku_presentations WHERE sku_code = :skuCode AND id != :id")
  boolean existsBySkuCodeAndIdNot(@Param("skuCode") String skuCode, @Param("id") Long id);

  @Query("SELECT COUNT(*) > 0 FROM sku_presentations WHERE sku_code = :skuCode")
  boolean existsBySkuCode(@Param("skuCode") String skuCode);

  @Modifying
  @Query(
      """
      UPDATE sku_presentations 
      SET presentation_name = :presentationName, 
          sku_code = :skuCode, 
          current_price = :currentPrice, 
          is_active = COALESCE(:isActive, is_active) 
      WHERE id = :id AND product_id = :productId
      """)
  int updateSku(
      @Param("id") Long id,
      @Param("productId") Long productId,
      @Param("skuCode") String skuCode,
      @Param("presentationName") String presentationName,
      @Param("currentPrice") BigDecimal currentPrice,
      @Param("isActive") Boolean isActive);

  @Modifying
  @Query(
      """
      UPDATE sku_presentations 
      SET presentation_name = :presentationName, 
          sku_code = :skuCode, 
          current_price = :currentPrice, 
          requires_stock = COALESCE(:requiresStock, requires_stock), 
          is_active = COALESCE(:isActive, is_active) 
      WHERE id = :id AND product_id = :productId
      """)
  int updateSku(
      @Param("id") Long id,
      @Param("productId") Long productId,
      @Param("skuCode") String skuCode,
      @Param("presentationName") String presentationName,
      @Param("currentPrice") BigDecimal currentPrice,
      @Param("isActive") Boolean isActive,
      @Param("requiresStock") Boolean requiresStock);

  @Modifying
  @Query(
      """
      INSERT INTO sku_presentations (product_id, sku_code, presentation_name, current_price, is_active) 
      VALUES (:productId, :skuCode, :presentationName, :currentPrice, :isActive)
      """)
  void insertSku(
      @Param("productId") Long productId,
      @Param("skuCode") String skuCode,
      @Param("presentationName") String presentationName,
      @Param("currentPrice") BigDecimal currentPrice,
      @Param("isActive") Boolean isActive);

  @Modifying
  @Query(
      """
      INSERT INTO sku_presentations (product_id, sku_code, presentation_name, current_price, requires_stock, is_active) 
      VALUES (:productId, :skuCode, :presentationName, :currentPrice, :requiresStock, :isActive)
      """)
  void insertSku(
      @Param("productId") Long productId,
      @Param("skuCode") String skuCode,
      @Param("presentationName") String presentationName,
      @Param("currentPrice") BigDecimal currentPrice,
      @Param("isActive") Boolean isActive,
      @Param("requiresStock") Boolean requiresStock);

  @Modifying
  @Query(
      """
      UPDATE sku_presentations 
      SET is_active = FALSE, deleted_at = NOW() 
      WHERE product_id = :productId AND id NOT IN (:excludedIds) AND is_active = TRUE
      """)
  int deactivateExcludedSkus(
      @Param("productId") Long productId,
      @Param("excludedIds") Collection<Long> excludedIds);

  @Modifying
  @Query(
      """
      UPDATE sku_presentations 
      SET is_active = FALSE, deleted_at = NOW() 
      WHERE product_id = :productId AND is_active = TRUE
      """)
  int deactivateAllSkusByProductId(@Param("productId") Long productId);
}
