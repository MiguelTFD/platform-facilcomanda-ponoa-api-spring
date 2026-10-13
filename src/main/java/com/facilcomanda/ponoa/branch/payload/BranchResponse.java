package com.facilcomanda.ponoa.branch.payload;

public record BranchResponse(
    Long id,
    String name,
    String code,
    String address,
    String phoneNumber,
    Boolean isActive) {}
