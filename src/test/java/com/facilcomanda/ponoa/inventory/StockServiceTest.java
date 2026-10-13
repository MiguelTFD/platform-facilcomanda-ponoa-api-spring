package com.facilcomanda.ponoa.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.global.exception.InsufficientStockException;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

  @Mock private StockRepository stockRepository;
  @Mock private StockEventPublisher stockEventPublisher;

  @InjectMocks private StockService stockService;

  @Test
  @DisplayName("Should initialize stock for a branch SKU")
  void shouldInitializeStock() {
    stockService.initializeSkuStock(1L, 100L, 50);
    verify(stockRepository).upsertStock(1L, 100L, 50);
    verify(stockEventPublisher).publishStockUpdate(1L, 100L, 50, true);
  }

  @Test
  @DisplayName("Should deduct stock atomically when sufficient stock exists")
  void shouldDeductStockSuccessfully() {
    when(stockRepository.deductStockAtomic(1L, 100L, 5)).thenReturn(1);
    StockLevel level = new StockLevel(1L, 1L, 100L, 45, OffsetDateTime.now());
    when(stockRepository.findByBranchIdAndSkuId(1L, 100L)).thenReturn(Optional.of(level));

    stockService.deductStock(1L, 100L, 5);

    verify(stockRepository).deductStockAtomic(1L, 100L, 5);
    verify(stockEventPublisher).publishStockUpdate(1L, 100L, 45, true);
  }

  @Test
  @DisplayName("Should broadcast depletion when stock reaches zero")
  void shouldBroadcastDepletionWhenStockReachesZero() {
    when(stockRepository.deductStockAtomic(1L, 100L, 5)).thenReturn(1);
    StockLevel level = new StockLevel(1L, 1L, 100L, 0, OffsetDateTime.now());
    when(stockRepository.findByBranchIdAndSkuId(1L, 100L)).thenReturn(Optional.of(level));

    stockService.deductStock(1L, 100L, 5);

    verify(stockRepository).deductStockAtomic(1L, 100L, 5);
    verify(stockEventPublisher).publishStockUpdate(1L, 100L, 0, false);
  }

  @Test
  @DisplayName("Should throw ResourceNotFoundException when SKU does not exist in branch")
  void shouldThrowWhenSkuNotFoundInBranch() {
    when(stockRepository.deductStockAtomic(1L, 100L, 5)).thenReturn(0);
    when(stockRepository.existsByBranchIdAndSkuId(1L, 100L)).thenReturn(false);

    assertThatThrownBy(() -> stockService.deductStock(1L, 100L, 5))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("not registered in stock for branch 1");
  }

  @Test
  @DisplayName("Should throw InsufficientStockException when quantity is less than requested")
  void shouldThrowWhenInsufficientStock() {
    when(stockRepository.deductStockAtomic(1L, 100L, 10)).thenReturn(0);
    when(stockRepository.existsByBranchIdAndSkuId(1L, 100L)).thenReturn(true);

    assertThatThrownBy(() -> stockService.deductStock(1L, 100L, 10))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Insufficient stock for SKU 100 in branch 1");
  }

  @Test
  @DisplayName("Should add stock atomically when record exists")
  void shouldAddStockSuccessfully() {
    when(stockRepository.addStockAtomic(1L, 100L, 10)).thenReturn(1);
    StockLevel level = new StockLevel(1L, 1L, 100L, 20, OffsetDateTime.now());
    when(stockRepository.findByBranchIdAndSkuId(1L, 100L)).thenReturn(Optional.of(level));

    stockService.addStock(1L, 100L, 10);

    verify(stockRepository).addStockAtomic(1L, 100L, 10);
    verify(stockEventPublisher).publishStockUpdate(1L, 100L, 20, true);
  }

  @Test
  @DisplayName("Should return StockLevel when found in branch")
  void shouldReturnStockLevel() {
    StockLevel level = new StockLevel(10L, 1L, 100L, 25, OffsetDateTime.now());
    when(stockRepository.findByBranchIdAndSkuId(1L, 100L)).thenReturn(Optional.of(level));

    StockLevel result = stockService.getStock(1L, 100L);

    assertThat(result.quantity()).isEqualTo(25);
    assertThat(result.branchId()).isEqualTo(1L);
    assertThat(result.skuId()).isEqualTo(100L);
  }

  @Test
  @DisplayName("Should return all stock levels for a branch")
  void shouldReturnAllStockForBranch() {
    StockLevel s1 = new StockLevel(1L, 1L, 101L, 10, null);
    StockLevel s2 = new StockLevel(2L, 1L, 102L, 20, null);
    when(stockRepository.findAllByBranchId(1L)).thenReturn(List.of(s1, s2));

    List<StockLevel> list = stockService.getStockByBranch(1L);

    assertThat(list).hasSize(2);
  }
}
