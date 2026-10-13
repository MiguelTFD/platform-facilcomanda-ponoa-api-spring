package com.facilcomanda.ponoa.billing.client;

import java.util.Optional;

public interface TaxIdLookupClient {
  Optional<TaxCustomerDto> lookupDni(String dni);
  Optional<TaxCustomerDto> lookupRuc(String ruc);
}
