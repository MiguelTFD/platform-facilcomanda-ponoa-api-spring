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
          SELECT p.id, p.name, b.name as brand_name, sp.current_price as starting_price
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
  @Query("UPDATE products SET is_active = FALSE, deleted_at = NOW() WHERE id = :id AND is_active = TRUE")
  int softDeleteProduct(@Param("id") Long id);
}
