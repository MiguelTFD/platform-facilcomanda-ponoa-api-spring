package com.facilcomanda.ponoa.global.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBranchResolver {
  Optional<Long> findBranchIdForUser(UUID userId);

  default List<Long> findBranchIdsForUser(UUID userId) {
    return findBranchIdForUser(userId).map(List::of).orElse(List.of());
  }
}
