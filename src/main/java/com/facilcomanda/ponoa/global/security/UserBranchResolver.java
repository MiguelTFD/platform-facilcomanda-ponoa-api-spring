package com.facilcomanda.ponoa.global.security;

import java.util.Optional;
import java.util.UUID;

public interface UserBranchResolver {
  Optional<Long> findBranchIdForUser(UUID userId);
}
