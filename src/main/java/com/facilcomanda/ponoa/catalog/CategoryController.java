package com.facilcomanda.ponoa.catalog;

import com.facilcomanda.ponoa.catalog.payload.CategoryResponse;
import com.facilcomanda.ponoa.catalog.payload.CreateCategoryRequest;
import com.facilcomanda.ponoa.catalog.payload.UpdateCategoryRequest;
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
@RequestMapping("/api/v1/categories")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @GetMapping
  public ResponseEntity<List<CategoryResponse>> listCategories() {
    return ResponseEntity.ok(categoryService.listActiveCategories());
  }

  @GetMapping("/{id}")
  public ResponseEntity<CategoryResponse> getCategory(@PathVariable Long id) {
    return ResponseEntity.ok(categoryService.getCategoryById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
    Long id = categoryService.createCategory(request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(id)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateCategory(
      @PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
    categoryService.updateCategory(id, request);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
    categoryService.deactivateCategory(id);
    return ResponseEntity.noContent().build();
  }
}
