package com.facilcomanda.ponoa.table;

public record TableStateChangedMessage(
    Long tableId,
    Long branchId,
    TableState state,
    Long orderId) {}
