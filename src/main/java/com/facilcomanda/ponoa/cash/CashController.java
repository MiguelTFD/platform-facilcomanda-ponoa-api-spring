package com.facilcomanda.ponoa.cash;

import com.facilcomanda.ponoa.cash.payload.AddCashMovementRequest;
import com.facilcomanda.ponoa.cash.payload.CashRegisterResponse;
import com.facilcomanda.ponoa.cash.payload.CashShiftResponse;
import com.facilcomanda.ponoa.cash.payload.CloseCashShiftRequest;
import com.facilcomanda.ponoa.cash.payload.CreateCashRegisterRequest;
import com.facilcomanda.ponoa.cash.payload.OpenCashShiftRequest;
import com.facilcomanda.ponoa.cash.payload.ZCutReportResponse;
import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/cash")
public class CashController {

  private final CashService cashService;
  private final BranchAccessValidator branchAccessValidator;

  public CashController(CashService cashService, BranchAccessValidator branchAccessValidator) {
    this.cashService = cashService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @PostMapping("/registers")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> createCashRegister(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateCashRegisterRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    Long registerId = cashService.createCashRegister(branchId, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(registerId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @GetMapping("/registers")
  public ResponseEntity<List<CashRegisterResponse>> listCashRegisters(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(cashService.listCashRegisters(branchId));
  }

  @org.springframework.web.bind.annotation.PutMapping("/registers/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateCashRegister(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody com.facilcomanda.ponoa.cash.payload.UpdateCashRegisterRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    cashService.updateCashRegister(branchId, id, request);

    return ResponseEntity.noContent().build();
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/registers/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deleteCashRegister(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    cashService.deleteCashRegister(branchId, id);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/shifts/open")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> openCashShift(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody OpenCashShiftRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    UUID cashierUserId = UUID.fromString(jwt.getSubject());
    String cashierName = resolveFullName(jwt);

    Long shiftId = cashService.openCashShift(branchId, cashierUserId, cashierName, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/cash/shifts/{id}")
            .buildAndExpand(shiftId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @PostMapping("/shifts/{shiftId}/movements")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> addCashMovement(
      @PathVariable Long shiftId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AddCashMovementRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    UUID userId = UUID.fromString(jwt.getSubject());

    Long movementId = cashService.addCashMovement(branchId, shiftId, userId, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(movementId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @GetMapping("/shifts/current")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<CashShiftResponse> getCurrentShift(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    UUID cashierUserId = UUID.fromString(jwt.getSubject());

    return ResponseEntity.ok(cashService.getCurrentShift(branchId, cashierUserId));
  }

  @GetMapping("/shifts/{shiftId}")
  public ResponseEntity<CashShiftResponse> getShiftById(
      @PathVariable Long shiftId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(cashService.getShiftById(branchId, shiftId));
  }

  @GetMapping("/shifts")
  public ResponseEntity<List<CashShiftResponse>> listShifts(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(cashService.listShifts(branchId));
  }

  @PostMapping("/shifts/{shiftId}/close")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<CashShiftResponse> closeCashShift(
      @PathVariable Long shiftId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CloseCashShiftRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    UUID cashierUserId = UUID.fromString(jwt.getSubject());

    CashShiftResponse response =
        cashService.closeCashShift(branchId, shiftId, cashierUserId, request);

    return ResponseEntity.ok(response);
  }

  @GetMapping("/shifts/{shiftId}/z-cut")
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<ZCutReportResponse> getZCutReport(
      @PathVariable Long shiftId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(cashService.getZCutReport(branchId, shiftId));
  }


  private String resolveFullName(Jwt jwt) {
    String given = jwt.getClaimAsString("given_name");
    String family = jwt.getClaimAsString("family_name");
    if (given != null && family != null) {
      return given + " " + family;
    }
    if (given != null) {
      return given;
    }
    String email = jwt.getClaimAsString("email");
    return email != null ? email : "Cajero";
  }
}
