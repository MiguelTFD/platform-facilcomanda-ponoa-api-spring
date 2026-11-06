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

  /**
   * Returns the production area (e.g. COCINA, PARRILLA, BARRA) configured for each SKU.
   * Defaults to 'COCINA' if not explicitly configured.
   */
  Map<Long, String> getProductionAreasForSkus(Set<Long> skuIds);

  /**
   * Returns presentation names for the requested SKUs.
   */
  Map<Long, String> getSkuNames(Set<Long> skuIds);

  /**
   * Returns whether each SKU requires stock control (true by default).
   * Non-stockable SKUs (e.g. made-to-order dishes) return false.
   */
  default Map<Long, Boolean> getRequiresStockForSkus(Set<Long> skuIds) {
    return Map.of();
  }

  /**
   * Returns the parent product ID for each requested SKU.
   */
  default Map<Long, Long> getProductIdsForSkus(Set<Long> skuIds) {
    return Map.of();
  }
}
