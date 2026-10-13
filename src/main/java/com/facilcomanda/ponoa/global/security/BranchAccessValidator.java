package com.facilcomanda.ponoa.global.security;

import java.util.Collections;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class BranchAccessValidator {

  public Long resolveAndValidateBranchId(Jwt jwt, Long requestedBranchId) {
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

  public List<Long> extractAllowedBranchIds(Jwt jwt) {
    if (jwt == null) {
      return Collections.emptyList();
    }

    // Check single 'branch_id' claim (number or string)
    Object singleBranch = jwt.getClaim("branch_id");
    if (singleBranch instanceof Number num) {
      return List.of(num.longValue());
    } else if (singleBranch instanceof String str && !str.isBlank()) {
      try {
        return List.of(Long.parseLong(str));
      } catch (NumberFormatException ignored) {
      }
    }

    // Check array 'branches' claim
    List<?> branches = jwt.getClaimAsStringList("branches");
    if (branches != null && !branches.isEmpty()) {
      return branches.stream()
          .map(Object::toString)
          .map(Long::parseLong)
          .toList();
    }

    return Collections.emptyList();
  }
}
