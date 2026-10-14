package com.facilcomanda.ponoa.cash;

import com.facilcomanda.ponoa.cash.payload.CreateExpenseRequest;
import com.facilcomanda.ponoa.cash.payload.ExpenseResponse;
import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

  private final CashService cashService;
  private final BranchAccessValidator branchAccessValidator;

  public ExpenseController(CashService cashService, BranchAccessValidator branchAccessValidator) {
    this.cashService = cashService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @GetMapping
  public ResponseEntity<List<ExpenseResponse>> listExpenses(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Long branchId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long targetBranchId = resolveBranchFilter(jwt, branchId != null ? branchId : branchIdHeader);
    OffsetDateTime fromDate = parseDateTime(from, LocalDate.now().minusDays(30).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime());
    OffsetDateTime toDate = parseDateTime(to, OffsetDateTime.now());

    return ResponseEntity.ok(cashService.listExpenses(targetBranchId, fromDate, toDate));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> recordExpense(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateExpenseRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    UUID userId = UUID.fromString(jwt.getSubject());

    Long expenseId = cashService.recordExpense(branchId, userId, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(expenseId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deleteExpense(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    cashService.deleteExpense(branchId, id);

    return ResponseEntity.noContent().build();
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
