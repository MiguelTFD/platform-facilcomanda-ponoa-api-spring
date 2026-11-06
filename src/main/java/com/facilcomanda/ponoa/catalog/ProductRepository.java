package com.facilcomanda.ponoa.catalog;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface ProductRepository extends CrudRepository<Product, Long> {

  @Query(
      """
          SELECT p.id, p.name, b.name as brand_name, sp.current_price as starting_price,
                 COALESCE(p.production_area, 'COCINA') as production_area
          FROM products p
          JOIN brands b ON p.brand_id = b.id
          JOIN sku_presentations sp ON sp.product_id = p.id
          WHERE sp.id = (
              SELECT MIN(id) FROM sku_presentations WHERE product_id = p.id AND is_active = TRUE
          )
          AND p.is_active = TRUE
      """)
  List<ProductSummaryView> findAllCatalogSummaries(Pageable pageable);

  @Modifying
  @Query("UPDATE products SET production_area = :productionArea WHERE id = :id AND is_active = TRUE")
  int updateProductionArea(@Param("id") Long id, @Param("productionArea") String productionArea);

  @Modifying
  @Query("UPDATE products SET is_active = FALSE, deleted_at = NOW() WHERE id = :id AND is_active = TRUE")
  int softDeleteProduct(@Param("id") Long id);

  @Modifying
  @Query(
      """
      UPDATE products 
      SET brand_id = :brandId, 
          name = :name, 
          base_description = :baseDescription, 
          production_area = :productionArea 
      WHERE id = :id AND is_active = TRUE
      """)
  int updateProductDetails(
      @Param("id") Long id,
      @Param("brandId") Long brandId,
      @Param("name") String name,
      @Param("baseDescription") String baseDescription,
      @Param("productionArea") String productionArea);

  @Modifying
  @Query("DELETE FROM product_categories WHERE product_id = :productId")
  void deleteProductCategories(@Param("productId") Long productId);

  @Modifying
  @Query("INSERT INTO product_categories (product_id, category_id) VALUES (:productId, :categoryId)")
  void insertProductCategory(@Param("productId") Long productId, @Param("categoryId") Long categoryId);
}
