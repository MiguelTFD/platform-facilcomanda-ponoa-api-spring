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

  @PutMapping("/{branchId}/products")
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

  private BranchProductResponse toResponse(BranchProduct bp) {
    return new BranchProductResponse(
        bp.id(),
        bp.branchId(),
        bp.skuId(),
        bp.price(),
        bp.isAvailable());
  }
}
