package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface SkuPricingRepository extends CrudRepository<SkuPriceView, Long> {

  @Query("SELECT id as sku_id, current_price FROM sku_presentations WHERE id IN (:skuIds)")
  List<SkuPriceView> findPricesBySkuIds(@Param("skuIds") Set<Long> skuIds);
}

record SkuPriceView(Long skuId, BigDecimal currentPrice) {}
