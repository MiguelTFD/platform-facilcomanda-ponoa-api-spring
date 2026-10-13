package com.facilcomanda.ponoa.table.payload;

import com.facilcomanda.ponoa.table.TableState;

public record TableResponse(
    Long id,
    Long branchId,
    Long floorId,
    String name,
    String description,
    TableState state,
    Integer chairs) {}
