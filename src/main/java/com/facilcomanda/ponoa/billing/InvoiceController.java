package com.facilcomanda.ponoa.billing;

import com.facilcomanda.ponoa.billing.payload.InvoiceResponse;
import com.facilcomanda.ponoa.billing.payload.PayOrderRequest;
import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/invoices")
@PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
public class InvoiceController {

  private final InvoiceService invoiceService;
  private final BranchAccessValidator branchAccessValidator;

  public InvoiceController(
      InvoiceService invoiceService, BranchAccessValidator branchAccessValidator) {
    this.invoiceService = invoiceService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @PostMapping("/pay")
  public ResponseEntity<InvoiceResponse> payOrder(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PayOrderRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    UUID cashierUserId = UUID.fromString(jwt.getSubject());
    String cashierEmail = jwt.getClaimAsString("email");

    InvoiceResponse response =
        invoiceService.payOrder(branchId, cashierUserId, cashierEmail, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();

    return ResponseEntity.created(location).body(response);
  }

  @GetMapping("/{id}")
  public ResponseEntity<InvoiceResponse> getInvoice(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(invoiceService.getInvoiceById(branchId, id));
  }

  @GetMapping("/orders/{orderId}")
  public ResponseEntity<InvoiceResponse> getInvoiceByOrderId(
      @PathVariable Long orderId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(invoiceService.getInvoiceByOrderId(branchId, orderId));
  }

  @GetMapping
  public ResponseEntity<List<InvoiceResponse>> listInvoices(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(invoiceService.listInvoices(branchId));
  }
}
