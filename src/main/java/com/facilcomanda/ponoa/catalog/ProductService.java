package com.facilcomanda.ponoa.catalog;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductService {

  private final ProductRepository productRepository;

  ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
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
}
