package com.facilcomanda.ponoa.sales;

import java.math.BigDecimal;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_items")
public record OrderItem(
    @Column("sku_id") Long skuId, Integer quantity, BigDecimal unitPriceAtPurchase) {}
