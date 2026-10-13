package com.facilcomanda.ponoa.catalog;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public interface CatalogPriceProvider {

  /**
   * Returns active, available prices for the requested SKUs in the specified branch.
   * If a SKU is marked as unavailable in the branch, it is omitted from the returned map.
   */
  Map<Long, BigDecimal> getAvailablePrices(Long branchId, Set<Long> skuIds);
}
