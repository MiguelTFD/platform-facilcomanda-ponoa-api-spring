package com.facilcomanda.ponoa.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BranchProductServiceTest {

  @Mock private BranchProductRepository branchProductRepository;

  @InjectMocks private BranchProductService branchProductService;

  @Test
  @DisplayName("Should configure branch product via upsert")
  void shouldConfigureBranchProduct() {
    branchProductService.configureBranchProduct(1L, 10L, new BigDecimal("18.50"), true);

    verify(branchProductRepository)
        .upsertBranchProduct(1L, 10L, new BigDecimal("18.50"), true);
  }

  @Test
  @DisplayName("Should return branch price when override exists and is available")
  void shouldReturnBranchOverridePrice() {
    BranchProduct override =
        new BranchProduct(
            1L, 1L, 10L, new BigDecimal("22.00"), true, OffsetDateTime.now(), OffsetDateTime.now());

    when(branchProductRepository.findByBranchIdAndSkuIds(1L, Set.of(10L)))
        .thenReturn(List.of(override));

    Map<Long, BigDecimal> prices = branchProductService.getAvailablePrices(1L, Set.of(10L));

    assertThat(prices).containsEntry(10L, new BigDecimal("22.00"));
  }

  @Test
  @DisplayName("Should omit SKU when branch marks it as unavailable")
  void shouldOmitUnavailableSku() {
    BranchProduct unavailable =
        new BranchProduct(
            1L, 1L, 10L, new BigDecimal("22.00"), false, OffsetDateTime.now(), OffsetDateTime.now());

    when(branchProductRepository.findByBranchIdAndSkuIds(1L, Set.of(10L)))
        .thenReturn(List.of(unavailable));

    Map<Long, BigDecimal> prices = branchProductService.getAvailablePrices(1L, Set.of(10L));

    assertThat(prices).doesNotContainKey(10L);
  }

  @Test
  @DisplayName("Should fallback to chain-level base price when no branch override exists")
  void shouldFallbackToBasePrice() {
    when(branchProductRepository.findByBranchIdAndSkuIds(1L, Set.of(10L)))
        .thenReturn(List.of());

    when(branchProductRepository.findBasePricesBySkuIds(Set.of(10L)))
        .thenReturn(List.of(new SkuBasePriceView(10L, new BigDecimal("15.00"))));

    Map<Long, BigDecimal> prices = branchProductService.getAvailablePrices(1L, Set.of(10L));

    assertThat(prices).containsEntry(10L, new BigDecimal("15.00"));
  }

  @Test
  @DisplayName("Should handle mixed overrides and fallbacks")
  void shouldHandleMixedOverridesAndFallbacks() {
    BranchProduct override =
        new BranchProduct(1L, 1L, 10L, new BigDecimal("25.00"), true, null, null);

    when(branchProductRepository.findByBranchIdAndSkuIds(1L, Set.of(10L, 20L)))
        .thenReturn(List.of(override));

    when(branchProductRepository.findBasePricesBySkuIds(Set.of(20L)))
        .thenReturn(List.of(new SkuBasePriceView(20L, new BigDecimal("30.00"))));

    Map<Long, BigDecimal> prices = branchProductService.getAvailablePrices(1L, Set.of(10L, 20L));

    assertThat(prices).containsEntry(10L, new BigDecimal("25.00"));
    assertThat(prices).containsEntry(20L, new BigDecimal("30.00"));
  }
}
