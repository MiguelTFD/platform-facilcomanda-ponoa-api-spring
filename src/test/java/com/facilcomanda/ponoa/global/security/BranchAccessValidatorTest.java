package com.facilcomanda.ponoa.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

class BranchAccessValidatorTest {

  private final BranchAccessValidator validator = new BranchAccessValidator();

  private Jwt createJwtWithClaims(Map<String, Object> claims) {
    return new Jwt(
        "mock-token-value",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("alg", "none"),
        claims);
  }

  @Test
  @DisplayName("Should throw AccessDeniedException when JWT has no branch claim")
  void shouldThrowWhenNoBranchAssigned() {
    Jwt jwt = createJwtWithClaims(Map.of("sub", "user-123", "email", "test@facilcomanda.com"));

    assertThatThrownBy(() -> validator.resolveAndValidateBranchId(jwt, 1L))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("no branch access");
  }

  @Test
  @DisplayName("Should resolve single branch automatically when request has no branchId")
  void shouldResolveSingleBranchAutomatically() {
    Jwt jwt = createJwtWithClaims(Map.of("branch_id", 10L));

    Long branchId = validator.resolveAndValidateBranchId(jwt, null);

    assertThat(branchId).isEqualTo(10L);
  }

  @Test
  @DisplayName("Should accept valid branch matching user's single branch")
  void shouldAcceptValidSingleBranch() {
    Jwt jwt = createJwtWithClaims(Map.of("branch_id", 10L));

    Long branchId = validator.resolveAndValidateBranchId(jwt, 10L);

    assertThat(branchId).isEqualTo(10L);
  }

  @Test
  @DisplayName("Should reject branch when requested branch does not match user's branch")
  void shouldRejectUnassignedBranch() {
    Jwt jwt = createJwtWithClaims(Map.of("branch_id", 10L));

    assertThatThrownBy(() -> validator.resolveAndValidateBranchId(jwt, 99L))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("not authorized");
  }

  @Test
  @DisplayName("Should accept valid branch from multiple assigned branches")
  void shouldAcceptFromMultipleBranches() {
    Jwt jwt = createJwtWithClaims(Map.of("branches", List.of("10", "20")));

    Long branchId = validator.resolveAndValidateBranchId(jwt, 20L);

    assertThat(branchId).isEqualTo(20L);
  }

  @Test
  @DisplayName("Should throw when multiple branches exist but none was requested")
  void shouldThrowWhenMultipleBranchesAndNoneRequested() {
    Jwt jwt = createJwtWithClaims(Map.of("branches", List.of(10L, 20L)));

    assertThatThrownBy(() -> validator.resolveAndValidateBranchId(jwt, null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Multiple branches available");
  }

  @Test
  @DisplayName("Should allow EMPRESA to access any requested branch without token branch claim")
  void shouldAllowEmpresaAccessAnyBranch() {
    Jwt jwt =
        createJwtWithClaims(
            Map.of(
                "sub", UUID.randomUUID().toString(),
                "realm_access", Map.of("roles", List.of("EMPRESA"))));

    Long branchId = validator.resolveAndValidateBranchId(jwt, 99L);

    assertThat(branchId).isEqualTo(99L);
  }

  @Test
  @DisplayName("Should fallback to UserBranchResolver when token lacks branch claims")
  void shouldFallbackToUserBranchResolver() {
    UUID userId = UUID.randomUUID();
    UserBranchResolver mockResolver = id -> id.equals(userId) ? Optional.of(42L) : Optional.empty();
    BranchAccessValidator resolverValidator = new BranchAccessValidator(Optional.of(mockResolver));

    Jwt jwt = createJwtWithClaims(Map.of("sub", userId.toString()));

    Long branchId = resolverValidator.resolveAndValidateBranchId(jwt, null);

    assertThat(branchId).isEqualTo(42L);
  }

  @Test
  @DisplayName("Should allow user assigned to multiple branches in DB to access any of their branches")
  void shouldAllowAccessToMultipleBranchesFromResolver() {
    UUID userId = UUID.randomUUID();
    UserBranchResolver multiBranchResolver =
        new UserBranchResolver() {
          @Override
          public Optional<Long> findBranchIdForUser(UUID id) {
            return id.equals(userId) ? Optional.of(10L) : Optional.empty();
          }

          @Override
          public List<Long> findBranchIdsForUser(UUID id) {
            return id.equals(userId) ? List.of(10L, 25L, 30L) : List.of();
          }
        };
    BranchAccessValidator resolverValidator =
        new BranchAccessValidator(Optional.of(multiBranchResolver));

    // Even if token only has primary branch_id = 10, DB resolver grants access to branch 25 and 30
    Jwt jwt = createJwtWithClaims(Map.of("sub", userId.toString(), "branch_id", 10L));

    assertThat(resolverValidator.resolveAndValidateBranchId(jwt, 25L)).isEqualTo(25L);
    assertThat(resolverValidator.resolveAndValidateBranchId(jwt, 30L)).isEqualTo(30L);
    assertThatThrownBy(() -> resolverValidator.resolveAndValidateBranchId(jwt, 99L))
        .isInstanceOf(AccessDeniedException.class);
  }
}
