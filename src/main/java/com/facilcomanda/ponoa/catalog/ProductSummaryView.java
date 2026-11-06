package com.facilcomanda.ponoa.catalog;

import java.math.BigDecimal;
import org.springframework.data.annotation.PersistenceCreator;

public record ProductSummaryView(
    Long id,
    String name,
    String brandName,
    BigDecimal startingPrice,
    String productionArea) {

  @PersistenceCreator
  public ProductSummaryView {}

  public ProductSummaryView(
      Long id, String name, String brandName, BigDecimal startingPrice) {
    this(id, name, brandName, startingPrice, "COCINA");
  }
}
