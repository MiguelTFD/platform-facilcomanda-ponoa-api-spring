package com.facilcomanda.ponoa.sales;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

  private final OrderService orderService;
  private final BranchAccessValidator branchAccessValidator;

  OrderController(OrderService orderService, BranchAccessValidator branchAccessValidator) {
    this.orderService = orderService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @PostMapping
  public ResponseEntity<Void> createOrder(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PlaceOrderRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    UUID userId = UUID.fromString(jwt.getSubject());
    String userEmail = jwt.getClaimAsString("email");

    Long orderId = orderService.placeOrder(branchId, userId, idempotencyKey, userEmail, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(orderId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @GetMapping("/{id}")
  public ResponseEntity<CustomerOrder> getOrder(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(orderService.getOrderByIdAndBranch(id, branchId));
  }

  @GetMapping
  public ResponseEntity<List<CustomerOrder>> listOrders(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(orderService.getOrdersByBranch(branchId));
  }
}
