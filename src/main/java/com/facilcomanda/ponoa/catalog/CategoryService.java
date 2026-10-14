package com.facilcomanda.ponoa.catalog;

import com.facilcomanda.ponoa.catalog.payload.CategoryResponse;
import com.facilcomanda.ponoa.catalog.payload.CreateCategoryRequest;
import com.facilcomanda.ponoa.catalog.payload.UpdateCategoryRequest;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

  private final CategoryRepository categoryRepository;

  CategoryService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> listActiveCategories() {
    return categoryRepository.findAllActive().stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public CategoryResponse getCategoryById(Long id) {
    return categoryRepository
        .findByIdActive(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));
  }

  @Transactional
  public Long createCategory(CreateCategoryRequest request) {
    String cleanName = request.name().trim();
    if (categoryRepository.existsByNameActive(cleanName)) {
      throw new DuplicateKeyException("Ya existe una categoría activa con el nombre: " + cleanName);
    }

    Category category = new Category(null, cleanName, request.description() != null ? request.description().trim() : null);
    return categoryRepository.save(category).id();
  }

  @Transactional
  public void updateCategory(Long id, UpdateCategoryRequest request) {
    Category existing = categoryRepository
        .findByIdActive(id)
        .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));

    String cleanName = request.name().trim();
    if (categoryRepository.existsByNameAndIdNotActive(cleanName, id)) {
      throw new DuplicateKeyException("Ya existe otra categoría activa con el nombre: " + cleanName);
    }

    categoryRepository.updateCategory(
        id, cleanName, request.description() != null ? request.description().trim() : existing.description());
  }

  @Transactional
  public void deactivateCategory(Long id) {
    int rows = categoryRepository.softDeleteCategory(id);
    if (rows == 0) {
      throw new ResourceNotFoundException("Categoría no encontrada con ID: " + id);
    }
  }

  private CategoryResponse toResponse(Category c) {
    return new CategoryResponse(c.id(), c.name(), c.description(), c.isActive());
  }
}
