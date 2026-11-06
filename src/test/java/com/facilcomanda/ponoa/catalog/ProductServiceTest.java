package com.facilcomanda.ponoa.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.catalog.payload.UpdateProductRequest;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private BrandRepository brandRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private SkuPresentationRepository skuPresentationRepository;

  @InjectMocks private ProductService productService;

  @Test
  @DisplayName("Should update product metadata and categories without touching SKUs when skus is null")
  void shouldUpdateProductWithoutTouchingSkus() {
    Product existingProduct =
        new Product(
            1L,
            10L,
            "Hamburguesa Clásica",
            "Carne 150g",
            "COCINA",
            Set.of(new SkuPresentation(4L, "HAM-01", "Unidad", new BigDecimal("20.00"))),
            Collections.emptySet(),
            Set.of(new ProductCategoryRef(100L)));

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(brandRepository.findByIdActive(10L)).thenReturn(Optional.of(new Brand(10L, "Gourmet Burgers", "Hamburguesas gourmet")));
    when(categoryRepository.findByIdActive(101L)).thenReturn(Optional.of(new Category(101L, "Platos Principales", "Categoría principal")));
    when(productRepository.updateProductDetails(1L, 10L, "Hamburguesa Doble", "Carne 300g", "COCINA"))
        .thenReturn(1);

    UpdateProductRequest request =
        new UpdateProductRequest(
            10L,
            "Hamburguesa Doble",
            "Carne 300g",
            "COCINA",
            Set.of(101L));

    productService.updateProduct(1L, request);

    // Verify productRepository.save is NEVER called (which would cause aggregate cascade delete)
    verify(productRepository, never()).save(any());

    // Verify targeted updates were performed
    verify(productRepository)
        .updateProductDetails(1L, 10L, "Hamburguesa Doble", "Carne 300g", "COCINA");
    verify(productRepository).deleteProductCategories(1L);
    verify(productRepository).insertProductCategory(1L, 101L);

    // Verify SKUs were NOT touched
    verify(skuPresentationRepository, never()).updateSku(anyLong(), anyLong(), any(), any(), any(), any());
    verify(skuPresentationRepository, never()).insertSku(anyLong(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should update existing SKU non-destructively and insert new SKU")
  void shouldUpdateExistingSkuAndInsertNewSku() {
    Product existingProduct =
        new Product(
            1L,
            10L,
            "Hamburguesa Clásica",
            "Carne 150g",
            "COCINA",
            Set.of(new SkuPresentation(4L, "HAM-01", "Unidad", new BigDecimal("20.00"))),
            Collections.emptySet(),
            Collections.emptySet());

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(brandRepository.findByIdActive(10L)).thenReturn(Optional.of(new Brand(10L, "Gourmet Burgers", "Hamburguesas gourmet")));
    when(productRepository.updateProductDetails(1L, 10L, "Hamburguesa Clásica", "Carne 150g", "COCINA"))
        .thenReturn(1);

    when(skuPresentationRepository.existsBySkuCodeAndIdNot("HAM-01", 4L)).thenReturn(false);
    when(skuPresentationRepository.updateSku(4L, 1L, "HAM-01", "Simple", new BigDecimal("22.00"), true))
        .thenReturn(1);

    when(skuPresentationRepository.existsBySkuCode("HAM-02")).thenReturn(false);

    UpdateProductRequest request =
        new UpdateProductRequest(
            10L,
            "Hamburguesa Clásica",
            "Carne 150g",
            "COCINA",
            Collections.emptySet(),
            Set.of(
                new UpdateProductRequest.SkuUpdateRequest(4L, "HAM-01", "Simple", new BigDecimal("22.00"), true),
                new UpdateProductRequest.SkuUpdateRequest(null, "HAM-02", "Doble", new BigDecimal("28.00"), true)));

    productService.updateProduct(1L, request);

    // Non-destructive SKU update
    verify(skuPresentationRepository)
        .updateSku(4L, 1L, "HAM-01", "Simple", new BigDecimal("22.00"), true);
    // New SKU insertion
    verify(skuPresentationRepository)
        .insertSku(1L, "HAM-02", "Doble", new BigDecimal("28.00"), true);
    // Product aggregate save is NEVER called
    verify(productRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw ResourceNotFoundException when brand is not active or not found")
  void shouldThrowWhenBrandNotFound() {
    Product existingProduct =
        new Product(1L, 10L, "Papas", null, "COCINA", Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(brandRepository.findByIdActive(999L)).thenReturn(Optional.empty());

    UpdateProductRequest request =
        new UpdateProductRequest(999L, "Papas", null, "COCINA", Collections.emptySet());

    assertThatThrownBy(() -> productService.updateProduct(1L, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Marca no encontrada");
  }

  @Test
  @DisplayName("Should soft-delete product and deactivate all its SKUs")
  void shouldSoftDeleteProductAndSkus() {
    when(productRepository.softDeleteProduct(1L)).thenReturn(1);

    productService.softDeleteProduct(1L);

    verify(productRepository).softDeleteProduct(1L);
    verify(skuPresentationRepository).deactivateAllSkusByProductId(1L);
  }

  @Test
  @DisplayName("Should update individual product production area directly")
  void shouldUpdateProductProductionAreaIndividually() {
    when(productRepository.updateProductionArea(1L, "BARRA")).thenReturn(1);

    productService.updateProductProductionArea(1L, "barra");

    verify(productRepository).updateProductionArea(1L, "BARRA");
  }

  @Test
  @DisplayName("Should persist requiresStock = false when updating or inserting non-stockable SKUs")
  void shouldPersistRequiresStockFlagOnSkus() {
    Product existingProduct =
        new Product(
            1L,
            10L,
            "Ceviche Clásico",
            "Plato a pedido",
            "COCINA",
            Set.of(new SkuPresentation(4L, "CEV-01", "Personal", new BigDecimal("35.00"), false)),
            Collections.emptySet(),
            Collections.emptySet());

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(brandRepository.findByIdActive(10L))
        .thenReturn(Optional.of(new Brand(10L, "Mariscos Perú", "Especialidad marina")));
    when(productRepository.updateProductDetails(
            1L, 10L, "Ceviche Clásico", "Plato a pedido", "BARRA_FRIA"))
        .thenReturn(1);

    when(skuPresentationRepository.existsBySkuCodeAndIdNot("CEV-01", 4L)).thenReturn(false);
    when(skuPresentationRepository.updateSku(
            4L, 1L, "CEV-01", "Personal", new BigDecimal("38.00"), true, false))
        .thenReturn(1);
    when(skuPresentationRepository.existsBySkuCode("CEV-02")).thenReturn(false);

    UpdateProductRequest request =
        new UpdateProductRequest(
            10L,
            "Ceviche Clásico",
            "Plato a pedido",
            "BARRA_FRIA",
            Collections.emptySet(),
            Set.of(
                new UpdateProductRequest.SkuUpdateRequest(
                    4L, "CEV-01", "Personal", new BigDecimal("38.00"), true, false),
                new UpdateProductRequest.SkuUpdateRequest(
                    null, "CEV-02", "Fuente Familiar", new BigDecimal("75.00"), true, false)));

    productService.updateProduct(1L, request);

    verify(skuPresentationRepository)
        .updateSku(4L, 1L, "CEV-01", "Personal", new BigDecimal("38.00"), true, false);
    verify(skuPresentationRepository)
        .insertSku(1L, "CEV-02", "Fuente Familiar", new BigDecimal("75.00"), true, false);
  }
}
