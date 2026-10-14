package com.facilcomanda.ponoa.catalog;

import com.facilcomanda.ponoa.catalog.payload.BranchProductResponse;
import com.facilcomanda.ponoa.catalog.payload.ConfigureBranchProductRequest;
import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/branches")
class BranchProductController {

  private final BranchProductService branchProductService;
  private final BranchAccessValidator branchAccessValidator;

  BranchProductController(
      BranchProductService branchProductService,
      BranchAccessValidator branchAccessValidator) {
    this.branchProductService = branchProductService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @PutMapping("/products")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> configureBranchProductInherited(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ConfigureBranchProductRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    branchProductService.configureBranchProduct(
        branchId, request.skuId(), request.price(), request.isAvailable());

    return ResponseEntity.ok().build();
  }

  @GetMapping("/products")
  public ResponseEntity<List<BranchProductResponse>> getBranchProductsInherited(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    List<BranchProductResponse> list =
        branchProductService.getBranchProducts(branchId).stream()
            .map(this::toResponse)
            .toList();

    return ResponseEntity.ok(list);
  }

  @PutMapping("/{branchId}/products")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> configureBranchProduct(
      @PathVariable Long branchId,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ConfigureBranchProductRequest request) {

    branchAccessValidator.resolveAndValidateBranchId(jwt, branchId);
    branchProductService.configureBranchProduct(
        branchId, request.skuId(), request.price(), request.isAvailable());

    return ResponseEntity.ok().build();
  }

  @GetMapping("/{branchId}/products")
  public ResponseEntity<List<BranchProductResponse>> getBranchProducts(
      @PathVariable Long branchId,
      @AuthenticationPrincipal Jwt jwt) {

    branchAccessValidator.resolveAndValidateBranchId(jwt, branchId);
    List<BranchProductResponse> list =
        branchProductService.getBranchProducts(branchId).stream()
            .map(this::toResponse)
            .toList();

    return ResponseEntity.ok(list);
  }

  private BranchProductResponse toResponse(BranchProductDetailedView bp) {
    return new BranchProductResponse(
        bp.id(),
        bp.branchId(),
        bp.skuId(),
        bp.skuCode(),
        bp.productName(),
        bp.presentationName(),
        bp.price(),
        bp.isAvailable());
  }
}
