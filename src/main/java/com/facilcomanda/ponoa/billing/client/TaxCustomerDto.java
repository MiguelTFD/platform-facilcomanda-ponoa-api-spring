package com.facilcomanda.ponoa.billing.client;

public record TaxCustomerDto(
    String name,
    String identityDocumentType,
    String identityDocumentNumber,
    String address) {}
