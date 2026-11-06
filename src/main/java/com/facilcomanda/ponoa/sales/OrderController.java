package com.facilcomanda.ponoa.sales;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
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
  public ResponseEntity<OrderResponse> getOrder(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(orderService.getEnrichedOrderByIdAndBranch(id, branchId));
  }

  @GetMapping
  public ResponseEntity<List<OrderResponse>> listOrders(
      @RequestParam(value = "status", required = false) String status,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(orderService.getEnrichedOrdersByBranch(branchId, status));
  }

  @GetMapping("/tables/{tableId}")
  public ResponseEntity<List<OrderResponse>> listActiveOrdersByTable(
      @PathVariable Long tableId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(orderService.getActiveOrdersByTable(branchId, tableId));
  }

  @PostMapping("/{id}/items")
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> addItems(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AddOrderItemsRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    orderService.addItemsToOrder(branchId, id, request);

    return ResponseEntity.ok().build();
  }

  @PutMapping("/{id}/items/{skuId}")
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateOrderItemPut(
      @PathVariable Long id,
      @PathVariable Long skuId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateOrderItemRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    orderService.updateOrderItem(branchId, id, skuId, request);

    return ResponseEntity.ok().build();
  }

  @PatchMapping("/{id}/items/{skuId}")
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateOrderItemPatch(
      @PathVariable Long id,
      @PathVariable Long skuId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateOrderItemRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    orderService.updateOrderItem(branchId, id, skuId, request);

    return ResponseEntity.ok().build();
  }

  @PatchMapping("/{id}/items/{skuId}/status")
  @PreAuthorize("hasRole('MESERO')")
  public ResponseEntity<Void> updateOrderItemStatus(
      @PathVariable Long id,
      @PathVariable Long skuId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateOrderItemStatusRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    orderService.updateOrderItemStatus(branchId, id, skuId, request.status());

    return ResponseEntity.ok().build();
  }

  @PostMapping("/{id}/items/{skuId}/attend")
  @PreAuthorize("hasRole('MESERO')")
  public ResponseEntity<Void> attendOrderItem(
      @PathVariable Long id,
      @PathVariable Long skuId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    orderService.attendOrderItem(branchId, id, skuId);

    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}/items/{skuId}")
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> cancelOrderItem(
      @PathVariable Long id,
      @PathVariable Long skuId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    orderService.cancelOrderItem(branchId, id, skuId);

    return ResponseEntity.ok().build();
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> cancelOrder(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CancelOrderRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    UUID userId = UUID.fromString(jwt.getSubject());
    orderService.cancelOrder(branchId, id, userId, request.reason());

    return ResponseEntity.ok().build();
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateOrderStatus(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateOrderStatusRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    orderService.updateOrderStatus(branchId, id, request.status());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{id}/deliver")
  @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> markOrderAsDelivered(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    orderService.markOrderAsDelivered(branchId, id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{id}/pre-check")
  public ResponseEntity<PreCheckResponse> getPreCheck(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(orderService.getOrderPreCheck(branchId, id));
  }

  @GetMapping("/tables/{tableId}/pre-check")
  public ResponseEntity<PreCheckResponse> getPreCheckByTable(
      @PathVariable Long tableId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(orderService.getOrderPreCheckByTable(branchId, tableId));
  }

  @PostMapping("/{id}/pre-check/print")
  public ResponseEntity<Void> printPreCheck(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Long targetPrinterId) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    orderService.requestPreCheckPrint(branchId, id, targetPrinterId);
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/tables/{tableId}/pre-check/print")
  public ResponseEntity<Void> printPreCheckByTable(
      @PathVariable Long tableId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Long targetPrinterId) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    orderService.requestPreCheckPrintByTable(branchId, tableId, targetPrinterId);
    return ResponseEntity.accepted().build();
  }
}
