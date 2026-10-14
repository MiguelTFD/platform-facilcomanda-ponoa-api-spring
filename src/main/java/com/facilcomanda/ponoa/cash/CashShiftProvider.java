package com.facilcomanda.ponoa.cash;

import java.util.Optional;
import java.util.UUID;

public interface CashShiftProvider {
  Optional<Long> findActiveShiftIdForCashier(Long branchId, UUID cashierUserId);
}
