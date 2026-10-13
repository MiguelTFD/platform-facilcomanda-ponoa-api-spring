package com.facilcomanda.ponoa.billing.payload;

public record CustomerLookupResponse(
    boolean found,
    String name,
    String identityDocumentType,
    String identityDocumentNumber,
    String phoneNumber,
    String address,
    String source,
    String message,
    boolean allowManualEntry) {

  public static CustomerLookupResponse found(
      String name,
      String identityDocumentType,
      String identityDocumentNumber,
      String phoneNumber,
      String address,
      String source) {
    return new CustomerLookupResponse(
        true, name, identityDocumentType, identityDocumentNumber, phoneNumber, address, source, null, false);
  }

  public static CustomerLookupResponse notFound(String message) {
    return new CustomerLookupResponse(
        false, null, null, null, null, null, "NOT_FOUND", message, true);
  }
}
