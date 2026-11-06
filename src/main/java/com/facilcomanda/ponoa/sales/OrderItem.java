package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_items")
public record OrderItem(
    @Column("sku_id") Long skuId,
    Integer quantity,
    @Column("unit_price_at_purchase") BigDecimal unitPriceAtPurchase,
    String comments,
    @Column("status") String status) {

  @PersistenceCreator
  public OrderItem(
      Long skuId,
      Integer quantity,
      BigDecimal unitPriceAtPurchase,
      String comments,
      String status) {
    this.skuId = skuId;
    this.quantity = quantity;
    this.unitPriceAtPurchase = unitPriceAtPurchase;
    this.comments = comments;
    this.status = (status == null || status.isBlank()) ? "PENDING" : status.trim().toUpperCase();
  }

  public OrderItem(
      Long skuId, Integer quantity, BigDecimal unitPriceAtPurchase, String comments) {
    this(skuId, quantity, unitPriceAtPurchase, comments, "PENDING");
  }

  public OrderItem(Long skuId, Integer quantity, BigDecimal unitPriceAtPurchase) {
    this(skuId, quantity, unitPriceAtPurchase, null, "PENDING");
  }

  public boolean isCancelled() {
    return "CANCELLED".equalsIgnoreCase(status);
  }

  public boolean isCompleted() {
    return "COMPLETED".equalsIgnoreCase(status);
  }

  public boolean isModified() {
    return "MODIFIED".equalsIgnoreCase(status);
  }

  public BigDecimal subtotal() {
    if (isCancelled()) {
      return BigDecimal.ZERO;
    }
    return unitPriceAtPurchase != null && quantity != null
        ? unitPriceAtPurchase.multiply(BigDecimal.valueOf(quantity))
        : BigDecimal.ZERO;
  }
}
