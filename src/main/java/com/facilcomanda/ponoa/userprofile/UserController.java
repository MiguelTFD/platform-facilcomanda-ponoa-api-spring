package com.facilcomanda.ponoa.userprofile;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import com.facilcomanda.ponoa.userprofile.payload.CreateAdminUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.CreateCompanyUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.CreateStaffUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.UserResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

  private final UserService userService;
  private final BranchAccessValidator branchAccessValidator;

  UserController(UserService userService, BranchAccessValidator branchAccessValidator) {
    this.userService = userService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @PostMapping("/company")
  public ResponseEntity<Void> createCompanyUser(@Valid @RequestBody CreateCompanyUserRequest request) {
    UUID userId = userService.createCompanyUser(request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/users/{id}")
            .buildAndExpand(userId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @PostMapping("/admin")
  @PreAuthorize("hasRole('EMPRESA')")
  public ResponseEntity<Void> createAdminUser(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateAdminUserRequest request) {

    UUID callerId = UUID.fromString(jwt.getSubject());
    UUID userId = userService.createAdminUser(callerId, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/users/{id}")
            .buildAndExpand(userId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @PostMapping("/staff")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> createStaffUser(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateStaffUserRequest request) {

    UUID callerId = UUID.fromString(jwt.getSubject());
    boolean isEmpresa = branchAccessValidator.isEmpresa(jwt);
    Long tokenBranchId = extractSingleBranchId(jwt);

    UUID userId = userService.createStaffUser(callerId, isEmpresa, tokenBranchId, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/users/{id}")
            .buildAndExpand(userId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @GetMapping
  public ResponseEntity<List<UserResponse>> listUsers(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(value = "branchId", required = false) Long branchId) {

    UUID callerId = UUID.fromString(jwt.getSubject());
    boolean isEmpresa = branchAccessValidator.isEmpresa(jwt);
    Long tokenBranchId = extractSingleBranchId(jwt);

    List<UserResponse> users = userService.listUsers(callerId, isEmpresa, tokenBranchId, branchId);
    return ResponseEntity.ok(users);
  }

  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getUserById(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id) {

    UUID callerId = UUID.fromString(jwt.getSubject());
    boolean isEmpresa = branchAccessValidator.isEmpresa(jwt);
    Long tokenBranchId = extractSingleBranchId(jwt);

    UserResponse user = userService.getUserById(callerId, isEmpresa, tokenBranchId, id);
    return ResponseEntity.ok(user);
  }

  @org.springframework.web.bind.annotation.PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateUser(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody com.facilcomanda.ponoa.userprofile.payload.UpdateUserRequest request) {

    UUID callerId = UUID.fromString(jwt.getSubject());
    boolean isEmpresa = branchAccessValidator.isEmpresa(jwt);
    Long tokenBranchId = extractSingleBranchId(jwt);

    userService.updateUser(callerId, isEmpresa, tokenBranchId, id, request);
    return ResponseEntity.noContent().build();
  }

  @org.springframework.web.bind.annotation.PatchMapping("/{id}/deactivate")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deactivateUser(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id) {

    UUID callerId = UUID.fromString(jwt.getSubject());
    boolean isEmpresa = branchAccessValidator.isEmpresa(jwt);
    Long tokenBranchId = extractSingleBranchId(jwt);

    userService.deactivateUser(callerId, isEmpresa, tokenBranchId, id);
    return ResponseEntity.noContent().build();
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deleteUser(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id) {

    UUID callerId = UUID.fromString(jwt.getSubject());
    boolean isEmpresa = branchAccessValidator.isEmpresa(jwt);
    Long tokenBranchId = extractSingleBranchId(jwt);

    userService.deactivateUser(callerId, isEmpresa, tokenBranchId, id);
    return ResponseEntity.noContent().build();
  }

  private Long extractSingleBranchId(Jwt jwt) {
    if (jwt == null) {
      return null;
    }
    Object claim = jwt.getClaim("branch_id");
    if (claim instanceof Number num) {
      return num.longValue();
    } else if (claim instanceof String str && !str.isBlank()) {
      try {
        return Long.parseLong(str);
      } catch (NumberFormatException ignored) {
      }
    }
    return null;
  }
}
