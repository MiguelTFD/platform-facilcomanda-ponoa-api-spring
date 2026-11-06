package com.facilcomanda.ponoa.userprofile.payload;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record AssignUserBranchesRequest(
    @NotEmpty(message = "Debe especificar al menos una sucursal")
    Set<Long> branchIds) {}
