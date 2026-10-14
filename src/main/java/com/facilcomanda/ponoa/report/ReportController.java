package com.facilcomanda.ponoa.report;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import com.facilcomanda.ponoa.report.payload.ProductSalesRankingResponse;
import com.facilcomanda.ponoa.report.payload.SalesSummaryResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
public class ReportController {

  private final ReportService reportService;
  private final BranchAccessValidator branchAccessValidator;

  public ReportController(ReportService reportService, BranchAccessValidator branchAccessValidator) {
    this.reportService = reportService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @GetMapping("/sales-summary")
  public ResponseEntity<SalesSummaryResponse> getSalesSummary(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Long branchId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long targetBranchId = resolveBranchFilter(jwt, branchId != null ? branchId : branchIdHeader);
    OffsetDateTime fromDate = parseDateTime(from, LocalDate.now().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime());
    OffsetDateTime toDate = parseDateTime(to, OffsetDateTime.now());

    return ResponseEntity.ok(reportService.getSalesSummary(targetBranchId, fromDate, toDate));
  }

  @GetMapping("/product-sales")
  public ResponseEntity<ProductSalesRankingResponse> getProductSales(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) Long branchId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long targetBranchId = resolveBranchFilter(jwt, branchId != null ? branchId : branchIdHeader);
    OffsetDateTime fromDate = parseDateTime(from, LocalDate.now().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime());
    OffsetDateTime toDate = parseDateTime(to, OffsetDateTime.now());

    return ResponseEntity.ok(reportService.getProductSalesRanking(targetBranchId, fromDate, toDate, limit));
  }

  private Long resolveBranchFilter(Jwt jwt, Long branchId) {
    if (jwt == null) {
      return branchId;
    }
    if (branchAccessValidator.isEmpresa(jwt)) {
      return branchId;
    }
    return branchAccessValidator.resolveAndValidateBranchId(jwt, branchId);
  }

  private OffsetDateTime parseDateTime(String input, OffsetDateTime defaultValue) {
    if (input == null || input.isBlank()) {
      return defaultValue;
    }
    try {
      if (input.length() == 10) {
        return LocalDate.parse(input).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
      }
      return OffsetDateTime.parse(input);
    } catch (DateTimeParseException e) {
      return defaultValue;
    }
  }
}
