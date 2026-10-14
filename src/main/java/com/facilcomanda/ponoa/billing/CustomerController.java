package com.facilcomanda.ponoa.billing;

import com.facilcomanda.ponoa.billing.payload.CustomerLookupResponse;
import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
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
@RequestMapping("/api/v1/customers")
public class CustomerController {

  private final CustomerLookupService customerLookupService;
  private final BranchAccessValidator branchAccessValidator;

  public CustomerController(
      CustomerLookupService customerLookupService,
      BranchAccessValidator branchAccessValidator) {
    this.customerLookupService = customerLookupService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @GetMapping("/lookup")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR', 'EMPRESA', 'MESERO')")
  public ResponseEntity<CustomerLookupResponse> lookupCustomer(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest request,
      @RequestParam(value = "documentType", required = false) String documentType,
      @RequestParam("documentNumber") String documentNumber) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    UUID userId = jwt != null && jwt.getSubject() != null ? UUID.fromString(jwt.getSubject()) : null;
    String clientIp = resolveClientIp(request);

    CustomerLookupResponse response =
        customerLookupService.lookupCustomer(branchId, userId, clientIp, documentType, documentNumber);

    return ResponseEntity.ok(response);
  }

  private String resolveClientIp(HttpServletRequest request) {
    if (request == null) return "UNKNOWN";
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr != null ? remoteAddr : "UNKNOWN";
  }
}
