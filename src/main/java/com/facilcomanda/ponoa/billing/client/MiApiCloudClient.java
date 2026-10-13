package com.facilcomanda.ponoa.billing.client;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MiApiCloudClient implements TaxIdLookupClient {

  private static final Logger log = LoggerFactory.getLogger(MiApiCloudClient.class);

  private final RestClient restClient;
  private final String token;

  public MiApiCloudClient(
      @Value("${app.tax-api.base-url:https://miapi.cloud/v1}") String baseUrl,
      @Value("${app.tax-api.token:}") String token,
      @Value("${app.tax-api.connect-timeout-ms:2000}") int connectTimeoutMs,
      @Value("${app.tax-api.read-timeout-ms:3000}") int readTimeoutMs) {

    this.token = token;

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .build();
  }

  @Override
  public Optional<TaxCustomerDto> lookupDni(String dni) {
    if (token == null || token.isBlank()) {
      log.debug("Tax API token is not configured; skipping remote DNI lookup for: {}", dni);
      return Optional.empty();
    }

    try {
      TaxApiDniResponse response =
          restClient
              .get()
              .uri("/dni/{dni}", dni)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
              .retrieve()
              .body(TaxApiDniResponse.class);

      if (response != null
          && Boolean.TRUE.equals(response.success())
          && response.datos() != null) {
        String fullName = response.datos().fullName();
        if (fullName != null && !fullName.isBlank()) {
          return Optional.of(
              new TaxCustomerDto(fullName, "DNI", dni, null));
        }
      }
    } catch (Exception e) {
      log.warn("Failed or timed out querying DNI {} from tax API: {}", dni, e.getMessage());
    }

    return Optional.empty();
  }

  @Override
  public Optional<TaxCustomerDto> lookupRuc(String ruc) {
    if (token == null || token.isBlank()) {
      log.debug("Tax API token is not configured; skipping remote RUC lookup for: {}", ruc);
      return Optional.empty();
    }

    try {
      TaxApiRucResponse response =
          restClient
              .get()
              .uri("/ruc/{ruc}", ruc)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
              .retrieve()
              .body(TaxApiRucResponse.class);

      if (response != null
          && Boolean.TRUE.equals(response.success())
          && response.datos() != null) {
        String razonSocial = response.datos().razonSocial();
        if (razonSocial != null && !razonSocial.isBlank()) {
          return Optional.of(
              new TaxCustomerDto(
                  razonSocial.trim(),
                  "RUC",
                  ruc,
                  response.datos().direccion()));
        }
      }
    } catch (Exception e) {
      log.warn("Failed or timed out querying RUC {} from tax API: {}", ruc, e.getMessage());
    }

    return Optional.empty();
  }
}
