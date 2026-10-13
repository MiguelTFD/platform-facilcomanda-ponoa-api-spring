package com.facilcomanda.ponoa.sales;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

  private final OrderService orderService;

  OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping
  public ResponseEntity<Void> createOrder(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PlaceOrderRequest request) {

    UUID userId = UUID.fromString(jwt.getSubject());
    String userEmail = jwt.getClaimAsString("email");
    Long orderId = orderService.placeOrder(userId, idempotencyKey, userEmail, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(orderId)
            .toUri();

    return ResponseEntity.created(location).build();
  }
}
