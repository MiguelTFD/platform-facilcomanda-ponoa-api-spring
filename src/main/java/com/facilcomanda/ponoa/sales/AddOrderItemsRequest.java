package com.facilcomanda.ponoa.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Set;

public record AddOrderItemsRequest(
    @NotEmpty Set<@Valid OrderItemPayload> items,
    String additionNotes) {

  public record OrderItemPayload(
      @NotNull Long skuId,
      @NotNull @Positive Integer quantity,
      String comments) {}
}
