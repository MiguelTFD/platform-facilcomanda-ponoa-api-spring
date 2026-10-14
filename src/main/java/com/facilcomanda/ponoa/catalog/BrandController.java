package com.facilcomanda.ponoa.catalog;

import com.facilcomanda.ponoa.catalog.payload.BrandResponse;
import com.facilcomanda.ponoa.catalog.payload.CreateBrandRequest;
import com.facilcomanda.ponoa.catalog.payload.UpdateBrandRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/catalog/brands")
public class BrandController {

  private final BrandService brandService;

  public BrandController(BrandService brandService) {
    this.brandService = brandService;
  }

  @GetMapping
  public ResponseEntity<List<BrandResponse>> listBrands() {
    return ResponseEntity.ok(brandService.listActiveBrands());
  }

  @GetMapping("/{id}")
  public ResponseEntity<BrandResponse> getBrand(@PathVariable Long id) {
    return ResponseEntity.ok(brandService.getBrandById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> createBrand(@Valid @RequestBody CreateBrandRequest request) {
    Long id = brandService.createBrand(request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(id)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateBrand(
      @PathVariable Long id, @Valid @RequestBody UpdateBrandRequest request) {
    brandService.updateBrand(id, request);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deleteBrand(@PathVariable Long id) {
    brandService.deactivateBrand(id);
    return ResponseEntity.noContent().build();
  }
}
