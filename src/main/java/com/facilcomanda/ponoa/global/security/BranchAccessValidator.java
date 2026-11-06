package com.facilcomanda.ponoa.global.security;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class BranchAccessValidator {

  private final UserBranchResolver userBranchResolver;

  public BranchAccessValidator(Optional<UserBranchResolver> userBranchResolver) {
    this.userBranchResolver = userBranchResolver.orElse(null);
  }

  public BranchAccessValidator() {
    this.userBranchResolver = null;
  }

  public Long resolveAndValidateBranchId(Jwt jwt, Long requestedBranchId) {
    if (isEmpresa(jwt)) {
      if (requestedBranchId != null) {
        return requestedBranchId;
      }
      List<Long> allowedBranches = extractAllowedBranchIds(jwt);
      if (allowedBranches.size() == 1) {
        return allowedBranches.get(0);
      }
      throw new AccessDeniedException(
          "Multiple branches available. You must specify a target branch.");
    }

    List<Long> allowedBranches = extractAllowedBranchIds(jwt);

    if (allowedBranches.isEmpty()) {
      throw new AccessDeniedException("User has no branch access assigned in token.");
    }

    if (requestedBranchId == null) {
      if (allowedBranches.size() == 1) {
        return allowedBranches.get(0);
      }
      throw new AccessDeniedException(
          "Multiple branches available. You must specify a target branch.");
    }

    if (!allowedBranches.contains(requestedBranchId)) {
      throw new AccessDeniedException(
          "User is not authorized for branch: " + requestedBranchId);
    }

    return requestedBranchId;
  }

  public boolean isEmpresa(Jwt jwt) {
    if (jwt == null) {
      return false;
    }
    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
    if (realmAccess != null) {
      Object rolesObj = realmAccess.get("roles");
      if (rolesObj instanceof List<?> roles) {
        return roles.stream().anyMatch(r -> "EMPRESA".equalsIgnoreCase(r.toString()));
      }
    }
    return false;
  }

  public List<Long> extractAllowedBranchIds(Jwt jwt) {
    if (jwt == null) {
      return Collections.emptyList();
    }

    java.util.LinkedHashSet<Long> resolvedBranches = new java.util.LinkedHashSet<>();

    // 1. Check single or collection 'branch_id' claim
    Object singleBranch = jwt.getClaim("branch_id");
    if (singleBranch instanceof Number num) {
      resolvedBranches.add(num.longValue());
    } else if (singleBranch instanceof String str && !str.isBlank()) {
      for (String part : str.split(",")) {
        try {
          resolvedBranches.add(Long.parseLong(part.trim()));
        } catch (NumberFormatException ignored) {
        }
      }
    } else if (singleBranch instanceof java.util.Collection<?> col) {
      for (Object item : col) {
        if (item != null) {
          try {
            resolvedBranches.add(Long.parseLong(item.toString().trim()));
          } catch (NumberFormatException ignored) {
          }
        }
      }
    }

    // 2. Check array 'branches' claim
    List<?> branches = jwt.getClaimAsStringList("branches");
    if (branches != null && !branches.isEmpty()) {
      branches.stream()
          .map(Object::toString)
          .map(String::trim)
          .filter(s -> s.matches("^\\d+$"))
          .map(Long::parseLong)
          .forEach(resolvedBranches::add);
    }

    // 3. Merge or fallback with userBranchResolver (multi-branch assignments in DB)
    if (userBranchResolver != null && jwt.getSubject() != null) {
      try {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<Long> dbBranchIds = userBranchResolver.findBranchIdsForUser(userId);
        if (dbBranchIds != null && !dbBranchIds.isEmpty()) {
          resolvedBranches.addAll(dbBranchIds);
        } else {
          userBranchResolver.findBranchIdForUser(userId).ifPresent(resolvedBranches::add);
        }
      } catch (IllegalArgumentException ignored) {
      }
    }

    return List.copyOf(resolvedBranches);
  }
}
