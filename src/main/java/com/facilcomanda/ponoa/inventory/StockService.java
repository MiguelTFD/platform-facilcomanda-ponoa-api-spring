package com.facilcomanda.ponoa.inventory;

import com.facilcomanda.ponoa.global.exception.InsufficientStockException;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

  private final StockRepository stockRepository;

  StockService(StockRepository stockRepository) {
    this.stockRepository = stockRepository;
  }

  @Transactional
  public void initializeSkuStock(Long skuId, int initialQuantity) {
    stockRepository.initializeStock(skuId, initialQuantity);
  }

  @Transactional
  public void deductStock(Long skuId, int amount) {
    int rowsAffected = stockRepository.deductStockAtomic(skuId, amount);

    if (rowsAffected == 0) {
      boolean exists = stockRepository.existsById(skuId);
      if (!exists) {
        throw new ResourceNotFoundException("SKU " + skuId + " is not in stock.");
      }
      throw new InsufficientStockException("Insufficient stock for SKU: " + skuId);
    }
  }

  @Transactional
  public void addStock(Long skuId, int amount) {
    int rowsAffected = stockRepository.addStockAtomic(skuId, amount);
    if (rowsAffected == 0) {
      // TODO: add a custom stock throws exception
      throw new ResourceNotFoundException("SKU " + skuId + " is not in stock.");
    }
  }

  @Transactional(readOnly = true)
  public StockLevel getStock(Long skuId) {
    return stockRepository
        .findById(skuId)
        .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for SKU: " + skuId));
  }
}
