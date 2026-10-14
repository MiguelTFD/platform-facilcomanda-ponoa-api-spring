package com.facilcomanda.ponoa.kitchen;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import com.facilcomanda.ponoa.kitchen.payload.KitchenTicketResponse;
import com.facilcomanda.ponoa.kitchen.payload.UpdateTicketStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kitchen/tickets")
@PreAuthorize("hasAnyRole('COCINERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA', 'MESERO')")
class KitchenTicketController {

  private final KitchenTicketService kitchenTicketService;
  private final BranchAccessValidator branchAccessValidator;

  KitchenTicketController(
      KitchenTicketService kitchenTicketService, BranchAccessValidator branchAccessValidator) {
    this.kitchenTicketService = kitchenTicketService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @GetMapping
  public ResponseEntity<List<KitchenTicketResponse>> getActiveTickets(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(kitchenTicketService.getActiveTicketsByBranch(branchId));
  }

  @GetMapping("/{id}")
  public ResponseEntity<KitchenTicketResponse> getTicketById(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    return ResponseEntity.ok(kitchenTicketService.getTicketById(branchId, id));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<KitchenTicketResponse> updateStatus(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateTicketStatusRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    KitchenTicketResponse updatedTicket =
        kitchenTicketService.updateTicketStatus(branchId, id, request.status());

    return ResponseEntity.ok(updatedTicket);
  }
}
