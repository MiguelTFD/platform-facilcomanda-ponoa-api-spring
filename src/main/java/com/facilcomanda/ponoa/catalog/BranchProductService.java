package com.facilcomanda.ponoa.catalog;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BranchProductService implements CatalogPriceProvider {

  private final BranchProductRepository branchProductRepository;

  BranchProductService(BranchProductRepository branchProductRepository) {
    this.branchProductRepository = branchProductRepository;
  }

  @Transactional
  public void configureBranchProduct(
      Long branchId, Long skuId, BigDecimal price, boolean isAvailable) {
    branchProductRepository.upsertBranchProduct(branchId, skuId, price, isAvailable);
  }

  @Transactional(readOnly = true)
  public List<BranchProductDetailedView> getBranchProducts(Long branchId) {
    return branchProductRepository.findAllDetailedByBranchId(branchId);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, BigDecimal> getAvailablePrices(Long branchId, Set<Long> skuIds) {
    if (skuIds == null || skuIds.isEmpty()) {
      return Map.of();
    }

    Map<Long, BigDecimal> resolvedPrices = new HashMap<>();
    Set<Long> remainingSkuIds = new HashSet<>(skuIds);

    // 1. Check branch-specific overrides
    List<BranchProduct> branchOverrides =
        branchProductRepository.findByBranchIdAndSkuIds(branchId, skuIds);

    for (BranchProduct override : branchOverrides) {
      remainingSkuIds.remove(override.skuId());
      if (Boolean.TRUE.equals(override.isAvailable())) {
        resolvedPrices.put(override.skuId(), override.price());
      }
      // If isAvailable is false, it is intentionally not added to resolvedPrices
    }

    // 2. For items with no branch override, fallback to chain-level base prices
    if (!remainingSkuIds.isEmpty()) {
      List<SkuBasePriceView> basePrices =
          branchProductRepository.findBasePricesBySkuIds(remainingSkuIds);

      for (SkuBasePriceView basePrice : basePrices) {
        resolvedPrices.put(basePrice.skuId(), basePrice.currentPrice());
      }
    }

    return resolvedPrices;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, String> getProductionAreasForSkus(Set<Long> skuIds) {
    if (skuIds == null || skuIds.isEmpty()) {
      return Map.of();
    }

    List<SkuProductionAreaView> views =
        branchProductRepository.findProductionAreasBySkuIds(skuIds);

    Map<Long, String> areaMap = new HashMap<>();
    for (SkuProductionAreaView view : views) {
      areaMap.put(
          view.skuId(), view.productionArea() != null ? view.productionArea() : "COCINA");
    }

    return areaMap;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, String> getSkuNames(Set<Long> skuIds) {
    if (skuIds == null || skuIds.isEmpty()) {
      return Map.of();
    }

    List<SkuNameView> views = branchProductRepository.findPresentationNamesBySkuIds(skuIds);

    Map<Long, String> nameMap = new HashMap<>();
    for (SkuNameView view : views) {
      if (view.presentationName() != null && !view.presentationName().isBlank()) {
        nameMap.put(view.skuId(), view.presentationName());
      }
    }

    return nameMap;
  }
}
