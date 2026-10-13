package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import com.facilcomanda.ponoa.printing.payload.ConfigureRouteRequest;
import com.facilcomanda.ponoa.printing.payload.PrintJobResponse;
import com.facilcomanda.ponoa.printing.payload.PrinterDeviceResponse;
import com.facilcomanda.ponoa.printing.payload.RegisterPrinterRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/printers")
class PrinterController {

  private final PrintJobService printJobService;
  private final BranchAccessValidator branchAccessValidator;

  PrinterController(
      PrintJobService printJobService, BranchAccessValidator branchAccessValidator) {
    this.printJobService = printJobService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @PostMapping
  public ResponseEntity<Void> registerPrinter(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody RegisterPrinterRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    Long printerId = printJobService.registerPrinter(branchId, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(printerId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @GetMapping
  public ResponseEntity<List<PrinterDeviceResponse>> listPrinters(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    List<PrinterDeviceResponse> printers =
        printJobService.getPrinters(branchId).stream()
            .map(
                p ->
                    new PrinterDeviceResponse(
                        p.id(),
                        p.branchId(),
                        p.name(),
                        p.ipAddress(),
                        p.port(),
                        p.connectionType(),
                        p.isActive()))
            .toList();

    return ResponseEntity.ok(printers);
  }

  @PostMapping("/{id}/routes")
  public ResponseEntity<Void> configureRoute(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ConfigureRouteRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    printJobService.configureRoute(branchId, id, request.documentType());

    return ResponseEntity.ok().build();
  }

  @GetMapping("/jobs")
  public ResponseEntity<List<PrintJobResponse>> listJobs(
      @RequestParam(required = false) PrintJobStatus status,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    List<PrintJobResponse> jobs =
        printJobService.getPrintJobsByBranch(branchId, status).stream()
            .map(
                j ->
                    new PrintJobResponse(
                        j.id(),
                        j.branchId(),
                        j.printerId(),
                        j.orderId(),
                        j.documentType(),
                        j.status(),
                        j.errorMessage(),
                        j.retryCount(),
                        j.createdAt()))
            .toList();

    return ResponseEntity.ok(jobs);
  }
}
