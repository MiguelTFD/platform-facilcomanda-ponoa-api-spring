package com.facilcomanda.ponoa.catalog;

import com.facilcomanda.ponoa.catalog.payload.ProductDetailResponse;
import com.facilcomanda.ponoa.catalog.payload.UpdateProductRequest;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductService {

  private final ProductRepository productRepository;
  private final BrandRepository brandRepository;
  private final CategoryRepository categoryRepository;

  ProductService(
      ProductRepository productRepository,
      BrandRepository brandRepository,
      CategoryRepository categoryRepository) {
    this.productRepository = productRepository;
    this.brandRepository = brandRepository;
    this.categoryRepository = categoryRepository;
  }

  @Transactional
  public Long createNewProduct(CreateProductRequest request) {
    Set<ProductCategoryRef> categoryRefs =
        request.categoryIds() == null
            ? Collections.emptySet()
            : request.categoryIds().stream()
                .map(ProductCategoryRef::new)
                .collect(Collectors.toSet());

    Set<SkuPresentation> skus =
        request.skus() == null
            ? Collections.emptySet()
            : request.skus().stream()
                .map(
                    sku ->
                        new SkuPresentation(
                            null, sku.skuCode(), sku.presentationName(), sku.currentPrice()))
                .collect(Collectors.toSet());

    Product newProduct =
        new Product(
            null,
            request.brandId(),
            request.name(),
            request.baseDescription(),
            request.productionArea() != null ? request.productionArea() : "COCINA",
            skus,
            Collections.emptySet(),
            categoryRefs);

    Product savedProduct = productRepository.save(newProduct);

    return savedProduct.id();
  }

  @Transactional(readOnly = true)
  public List<ProductSummaryView> getCatalogSummaries(int pageNumber, int pageSize) {
    return productRepository.findAllCatalogSummaries(PageRequest.of(pageNumber, pageSize));
  }

  @Transactional(readOnly = true)
  public ProductDetailResponse getProductById(Long id) {
    Product product =
        productRepository
            .findById(id)
            .filter(p -> Boolean.TRUE.equals(p.isActive()))
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

    String brandName =
        product.brandId() != null
            ? brandRepository.findById(product.brandId()).map(Brand::name).orElse("Desconocido")
            : "Sin Marca";

    Set<Long> categoryIds =
        product.categoryRefs() != null
            ? product.categoryRefs().stream().map(ProductCategoryRef::categoryId).collect(Collectors.toSet())
            : Collections.emptySet();

    Map<Long, String> categoryNames =
        categoryIds.isEmpty()
            ? Map.of()
            : StreamSupport.stream(categoryRepository.findAllById(categoryIds).spliterator(), false)
                .collect(Collectors.toMap(Category::id, Category::name));

    List<ProductDetailResponse.CategoryItem> categoryItems =
        categoryIds.stream()
            .map(cid -> new ProductDetailResponse.CategoryItem(cid, categoryNames.getOrDefault(cid, "Categoría " + cid)))
            .toList();

    List<ProductDetailResponse.SkuItem> skuItems =
        product.skus() != null
            ? product.skus().stream()
                .filter(s -> Boolean.TRUE.equals(s.isActive()))
                .map(
                    s ->
                        new ProductDetailResponse.SkuItem(
                            s.id(), s.skuCode(), s.presentationName(), s.currentPrice(), s.isActive()))
                .toList()
            : Collections.emptyList();

    return new ProductDetailResponse(
        product.id(),
        product.brandId(),
        brandName,
        product.name(),
        product.baseDescription(),
        product.productionArea() != null ? product.productionArea() : "COCINA",
        product.isActive(),
        categoryItems,
        skuItems);
  }

  @Transactional
  public void updateProduct(Long id, UpdateProductRequest request) {
    Product product =
        productRepository
            .findById(id)
            .filter(p -> Boolean.TRUE.equals(p.isActive()))
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

    Set<ProductCategoryRef> newCategoryRefs =
        request.categoryIds() == null
            ? Collections.emptySet()
            : request.categoryIds().stream()
                .map(ProductCategoryRef::new)
                .collect(Collectors.toSet());

    Product updated =
        new Product(
            product.id(),
            request.brandId(),
            request.name().trim(),
            request.baseDescription() != null ? request.baseDescription().trim() : null,
            request.productionArea() != null ? request.productionArea().trim() : product.productionArea(),
            product.isActive(),
            product.deletedAt(),
            product.skus(),
            product.pictures(),
            newCategoryRefs);

    productRepository.save(updated);
  }

  @Transactional
  public void softDeleteProduct(Long id) {
    int rows = productRepository.softDeleteProduct(id);
    if (rows == 0) {
      throw new ResourceNotFoundException("Producto no encontrado con ID: " + id);
    }
  }
}
