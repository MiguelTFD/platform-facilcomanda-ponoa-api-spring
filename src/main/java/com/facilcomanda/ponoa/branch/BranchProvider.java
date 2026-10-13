package com.facilcomanda.ponoa.branch;

public interface BranchProvider {
  boolean existsBranch(Long branchId);
  boolean isBranchActive(Long branchId);
}
