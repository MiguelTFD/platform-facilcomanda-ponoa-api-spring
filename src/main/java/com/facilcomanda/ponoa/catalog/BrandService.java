package com.facilcomanda.ponoa.catalog;

import com.facilcomanda.ponoa.catalog.payload.BrandResponse;
import com.facilcomanda.ponoa.catalog.payload.CreateBrandRequest;
import com.facilcomanda.ponoa.catalog.payload.UpdateBrandRequest;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandService {

  private final BrandRepository brandRepository;

  BrandService(BrandRepository brandRepository) {
    this.brandRepository = brandRepository;
  }

  @Transactional(readOnly = true)
  public List<BrandResponse> listActiveBrands() {
    return brandRepository.findAllActive().stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public BrandResponse getBrandById(Long id) {
    return brandRepository
        .findByIdActive(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada con ID: " + id));
  }

  @Transactional
  public Long createBrand(CreateBrandRequest request) {
    String cleanName = request.name().trim();
    if (brandRepository.existsByNameActive(cleanName)) {
      throw new DuplicateKeyException("Ya existe una marca activa con el nombre: " + cleanName);
    }

    Brand brand = new Brand(null, cleanName, request.description() != null ? request.description().trim() : null);
    return brandRepository.save(brand).id();
  }

  @Transactional
  public void updateBrand(Long id, UpdateBrandRequest request) {
    Brand existing = brandRepository
        .findByIdActive(id)
        .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada con ID: " + id));

    String cleanName = request.name().trim();
    if (brandRepository.existsByNameAndIdNotActive(cleanName, id)) {
      throw new DuplicateKeyException("Ya existe otra marca activa con el nombre: " + cleanName);
    }

    brandRepository.updateBrand(
        id, cleanName, request.description() != null ? request.description().trim() : existing.description());
  }

  @Transactional
  public void deactivateBrand(Long id) {
    int rows = brandRepository.softDeleteBrand(id);
    if (rows == 0) {
      throw new ResourceNotFoundException("Marca no encontrada con ID: " + id);
    }
  }

  private BrandResponse toResponse(Brand b) {
    return new BrandResponse(b.id(), b.name(), b.description(), b.isActive());
  }
}
