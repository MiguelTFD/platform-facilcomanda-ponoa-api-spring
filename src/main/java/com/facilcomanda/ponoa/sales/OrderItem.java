package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_items")
public record OrderItem(
    @Column("sku_id") Long skuId,
    Integer quantity,
    @Column("unit_price_at_purchase") BigDecimal unitPriceAtPurchase,
    String comments) {

  public OrderItem(Long skuId, Integer quantity, BigDecimal unitPriceAtPurchase) {
    this(skuId, quantity, unitPriceAtPurchase, null);
  }

  public BigDecimal subtotal() {
    return unitPriceAtPurchase != null && quantity != null
        ? unitPriceAtPurchase.multiply(BigDecimal.valueOf(quantity))
        : BigDecimal.ZERO;
  }
}
