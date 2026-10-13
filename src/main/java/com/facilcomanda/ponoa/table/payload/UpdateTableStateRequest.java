package com.facilcomanda.ponoa.table.payload;

import com.facilcomanda.ponoa.table.TableState;
import jakarta.validation.constraints.NotNull;

public record UpdateTableStateRequest(
    @NotNull TableState state) {}
