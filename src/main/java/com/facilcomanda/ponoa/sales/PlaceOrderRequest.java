package com.facilcomanda.ponoa.sales;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Set;

public record PlaceOrderRequest(
    @NotEmpty(message = "The order must contain at least one item") Set<OrderItemPayload> items) {
  public record OrderItemPayload(@NotNull Long skuId, @NotNull @Positive Integer quantity) {}
}
