package com.facilcomanda.ponoa.catalog;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/catalog/products")
class ProductController {

  private final ProductService productService;

  ProductController(ProductService productService) {
    this.productService = productService;
  }

  @PostMapping
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> createProduct(@Valid @RequestBody CreateProductRequest request) {
    Long productId = productService.createNewProduct(request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(productId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @GetMapping
  public ResponseEntity<List<ProductSummaryView>> listCatalog(
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "20") int pageSize) {
    if (pageSize > 60) {
      pageSize = 60;
    }
    return ResponseEntity.ok(productService.getCatalogSummaries(pageNumber, pageSize));
  }

  @GetMapping("/{id}")
  public ResponseEntity<com.facilcomanda.ponoa.catalog.payload.ProductDetailResponse> getProductById(
      @PathVariable Long id) {
    return ResponseEntity.ok(productService.getProductById(id));
  }

  @PutMapping("/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateProduct(
      @PathVariable Long id,
      @Valid @RequestBody com.facilcomanda.ponoa.catalog.payload.UpdateProductRequest request) {
    productService.updateProduct(id, request);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/production-area")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateProductProductionArea(
      @PathVariable Long id,
      @Valid @RequestBody com.facilcomanda.ponoa.catalog.payload.UpdateProductProductionAreaRequest request) {
    productService.updateProductProductionArea(id, request.productionArea());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
    productService.softDeleteProduct(id);
    return ResponseEntity.noContent().build();
  }
}
