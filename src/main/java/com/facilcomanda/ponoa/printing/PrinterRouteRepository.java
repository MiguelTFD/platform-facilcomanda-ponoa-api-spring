package com.facilcomanda.ponoa.printing;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface PrinterRouteRepository extends CrudRepository<PrinterRoute, Long> {

  List<PrinterRoute> findAllByBranchIdAndDocumentType(
      Long branchId, PrintDocumentType documentType);

  @Query(
      "SELECT * FROM printer_routes "
          + "WHERE branch_id = :branchId "
          + "  AND document_type = :documentType "
          + "  AND (production_area = :productionArea OR production_area IS NULL)")
  List<PrinterRoute> findMatchingRoutes(
      @Param("branchId") Long branchId,
      @Param("documentType") String documentType,
      @Param("productionArea") String productionArea);

  @Query(
      "SELECT * FROM printer_routes "
          + "WHERE branch_id = :branchId "
          + "  AND document_type = :documentType "
          + "  AND product_id IN (:productIds)")
  List<PrinterRoute> findMatchingRoutesByProductIds(
      @Param("branchId") Long branchId,
      @Param("documentType") String documentType,
      @Param("productIds") java.util.Collection<Long> productIds);

  List<PrinterRoute> findAllByBranchIdAndPrinterId(Long branchId, Long printerId);

  boolean existsByBranchIdAndPrinterIdAndDocumentTypeAndProductionArea(
      Long branchId, Long printerId, PrintDocumentType documentType, String productionArea);

  boolean existsByBranchIdAndPrinterIdAndDocumentTypeAndProductId(
      Long branchId, Long printerId, PrintDocumentType documentType, Long productId);

  boolean existsByBranchIdAndPrinterIdAndDocumentType(
      Long branchId, Long printerId, PrintDocumentType documentType);
}
