package com.facilcomanda.ponoa.inventory;

import com.facilcomanda.ponoa.global.exception.InsufficientStockException;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

  private final StockRepository stockRepository;
  private final StockEventPublisher stockEventPublisher;

  StockService(StockRepository stockRepository, StockEventPublisher stockEventPublisher) {
    this.stockRepository = stockRepository;
    this.stockEventPublisher = stockEventPublisher;
  }

  @Transactional
  public void initializeSkuStock(Long branchId, Long skuId, int initialQuantity) {
    stockRepository.upsertStock(branchId, skuId, initialQuantity);
    stockEventPublisher.publishStockUpdate(branchId, skuId, initialQuantity, initialQuantity > 0);
  }

  @Transactional
  public void deductStock(Long branchId, Long skuId, int amount) {
    int rowsAffected = stockRepository.deductStockAtomic(branchId, skuId, amount);

    if (rowsAffected == 0) {
      boolean exists = stockRepository.existsByBranchIdAndSkuId(branchId, skuId);
      if (!exists) {
        throw new ResourceNotFoundException(
            "SKU " + skuId + " is not registered in stock for branch " + branchId);
      }
      throw new InsufficientStockException(
          "Insufficient stock for SKU " + skuId + " in branch " + branchId);
    }

    stockRepository
        .findByBranchIdAndSkuId(branchId, skuId)
        .ifPresent(
            level ->
                stockEventPublisher.publishStockUpdate(
                    branchId, skuId, level.quantity(), level.quantity() > 0));
  }

  @Transactional
  public void addStock(Long branchId, Long skuId, int amount) {
    int rowsAffected = stockRepository.addStockAtomic(branchId, skuId, amount);
    if (rowsAffected == 0) {
      throw new ResourceNotFoundException(
          "SKU " + skuId + " is not registered in stock for branch " + branchId);
    }

    stockRepository
        .findByBranchIdAndSkuId(branchId, skuId)
        .ifPresent(
            level ->
                stockEventPublisher.publishStockUpdate(
                    branchId, skuId, level.quantity(), level.quantity() > 0));
  }

  @Transactional(readOnly = true)
  public StockLevel getStock(Long branchId, Long skuId) {
    return stockRepository
        .findByBranchIdAndSkuId(branchId, skuId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Stock not found for SKU " + skuId + " in branch " + branchId));
  }

  @Transactional(readOnly = true)
  public List<StockLevel> getStockByBranch(Long branchId) {
    return stockRepository.findAllByBranchId(branchId);
  }
}
