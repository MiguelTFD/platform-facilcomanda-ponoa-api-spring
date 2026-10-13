package com.facilcomanda.ponoa.sales;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Set;

public record PlaceOrderRequest(
    Long tableId,
    String orderType,
    String notes,
    @NotEmpty(message = "The order must contain at least one item") Set<OrderItemPayload> items) {

  public PlaceOrderRequest(Set<OrderItemPayload> items) {
    this(null, "DINE_IN", null, items);
  }

  public record OrderItemPayload(
      @NotNull Long skuId,
      @NotNull @Positive Integer quantity,
      String comments) {

    public OrderItemPayload(Long skuId, Integer quantity) {
      this(skuId, quantity, null);
    }
  }
}
