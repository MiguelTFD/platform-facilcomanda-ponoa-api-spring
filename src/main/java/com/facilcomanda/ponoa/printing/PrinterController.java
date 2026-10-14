package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import com.facilcomanda.ponoa.printing.payload.ConfigureRouteRequest;
import com.facilcomanda.ponoa.printing.payload.PrintCashierReceiptRequest;
import com.facilcomanda.ponoa.printing.payload.PrintJobResponse;
import com.facilcomanda.ponoa.printing.payload.PrinterDeviceResponse;
import com.facilcomanda.ponoa.printing.payload.RegisterPrinterRequest;
import com.facilcomanda.ponoa.printing.payload.ReprintKitchenOrderRequest;
import com.facilcomanda.ponoa.printing.payload.ReprintReceiptRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
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
                        p.terminalCode(),
                        p.isActive()))
            .toList();

    return ResponseEntity.ok(printers);
  }

  @org.springframework.web.bind.annotation.PutMapping("/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updatePrinter(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody com.facilcomanda.ponoa.printing.payload.UpdatePrinterRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    printJobService.updatePrinter(branchId, id, request);

    return ResponseEntity.noContent().build();
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deletePrinter(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    printJobService.deletePrinter(branchId, id);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/routes")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> configureRoute(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ConfigureRouteRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    printJobService.configureRoute(branchId, id, request.documentType(), request.productionArea());

    return ResponseEntity.ok().build();
  }

  @PostMapping("/receipts")
  public ResponseEntity<Void> printCashierReceipt(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PrintCashierReceiptRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    printJobService.enqueueCashierReceipt(
        branchId, request.orderId(), request.terminalCode(), request.idempotencyKey());

    return ResponseEntity.accepted().build();
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

  @PostMapping("/kitchen/reprint")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('COCINERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Map<String, Object>> reprintKitchenOrder(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ReprintKitchenOrderRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    List<Long> jobIds =
        printJobService.reprintKitchenOrder(
             branchId, request.orderId(), request.productionArea(), request.targetPrinterId());

    return ResponseEntity.accepted().body(Map.of("jobIds", jobIds, "reprinted", true));
  }

  @PostMapping("/receipts/reprint")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Map<String, Object>> reprintReceipt(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ReprintReceiptRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    Long jobId =
        printJobService.reprintCashierReceipt(
            branchId,
            request.orderId(),
            request.terminalCode(),
            request.targetPrinterId(),
            request.reason());

    return ResponseEntity.accepted().body(Map.of("jobId", jobId, "reprinted", true));
  }

  @PostMapping("/jobs/{id}/retry")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR', 'EMPRESA', 'COCINERO')")
  public ResponseEntity<Map<String, Object>> retryJob(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    Long jobId = printJobService.retryPrintJob(branchId, id);

    return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "RETRYING"));
  }

  @PostMapping("/pre-check")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Map<String, Object>> printPreCheck(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody com.facilcomanda.ponoa.printing.payload.PrintPreCheckRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    Long jobId =
        printJobService.requestPreCheckPrint(branchId, request.orderId(), request.targetPrinterId());

    return ResponseEntity.accepted().body(Map.of("jobId", jobId, "printed", true));
  }
}
