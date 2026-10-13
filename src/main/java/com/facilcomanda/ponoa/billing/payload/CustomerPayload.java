package com.facilcomanda.ponoa.billing.payload;

import jakarta.validation.constraints.NotBlank;

public record CustomerPayload(
    @NotBlank(message = "Customer name is required") String name,
    @NotBlank(message = "Document type is required") String identityDocumentType,
    @NotBlank(message = "Document number is required") String identityDocumentNumber,
    String phoneNumber,
    String address) {}
