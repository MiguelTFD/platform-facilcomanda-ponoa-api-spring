package com.facilcomanda.ponoa.inventory;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import com.facilcomanda.ponoa.inventory.payload.InitializeStockRequest;
import com.facilcomanda.ponoa.inventory.payload.StockResponse;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/v1/inventory")
class StockController {

  private final StockService stockService;
  private final BranchAccessValidator branchAccessValidator;

  StockController(StockService stockService, BranchAccessValidator branchAccessValidator) {
    this.stockService = stockService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @PostMapping
  public ResponseEntity<Void> initializeStock(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody InitializeStockRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    stockService.initializeSkuStock(branchId, request.skuId(), request.quantity());

    return ResponseEntity.ok().build();
  }

  @GetMapping
  public ResponseEntity<List<StockResponse>> getStockByBranch(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    List<StockResponse> stock =
        stockService.getStockByBranch(branchId).stream().map(this::toResponse).toList();

    return ResponseEntity.ok(stock);
  }

  @GetMapping("/skus/{skuId}")
  public ResponseEntity<StockResponse> getStockForSku(
      @PathVariable Long skuId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    StockLevel stock = stockService.getStock(branchId, skuId);

    return ResponseEntity.ok(toResponse(stock));
  }

  private StockResponse toResponse(StockLevel stock) {
    return new StockResponse(
        stock.id(),
        stock.branchId(),
        stock.skuId(),
        stock.quantity(),
        stock.updatedAt());
  }
}
