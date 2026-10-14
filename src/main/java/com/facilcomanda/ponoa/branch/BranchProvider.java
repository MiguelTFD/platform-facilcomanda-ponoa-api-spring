package com.facilcomanda.ponoa.branch;

import java.util.Optional;

public interface BranchProvider {
  boolean existsBranch(Long branchId);
  boolean isBranchActive(Long branchId);
  Optional<String> findBranchName(Long branchId);
}
