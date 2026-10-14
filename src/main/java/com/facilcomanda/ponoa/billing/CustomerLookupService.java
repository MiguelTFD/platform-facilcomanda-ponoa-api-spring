package com.facilcomanda.ponoa.billing;

import com.facilcomanda.ponoa.billing.client.TaxCustomerDto;
import com.facilcomanda.ponoa.billing.client.TaxIdLookupClient;
import com.facilcomanda.ponoa.billing.payload.CustomerLookupResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerLookupService {

  private static final int MAX_CACHE_ENTRIES = 5000;
  private static final long CACHE_TTL_SECONDS = 900; // 15 minutes

  private final CustomerRepository customerRepository;
  private final TaxIdLookupClient taxIdLookupClient;
  private final CustomerAntiAbuseService customerAntiAbuseService;
  private final Map<String, CacheEntry> localCache = new ConcurrentHashMap<>();

  public CustomerLookupService(
      CustomerRepository customerRepository,
      TaxIdLookupClient taxIdLookupClient,
      CustomerAntiAbuseService customerAntiAbuseService) {
    this.customerRepository = customerRepository;
    this.taxIdLookupClient = taxIdLookupClient;
    this.customerAntiAbuseService = customerAntiAbuseService;
  }

  public CustomerLookupService(
      CustomerRepository customerRepository, TaxIdLookupClient taxIdLookupClient) {
    this(customerRepository, taxIdLookupClient, new CustomerAntiAbuseService(15, 40, 20, 500));
  }

  @Transactional(readOnly = true)
  public CustomerLookupResponse lookupCustomer(String requestedDocType, String documentNumber) {
    return lookupCustomer(null, null, null, requestedDocType, documentNumber);
  }

  @Transactional(readOnly = true)
  public CustomerLookupResponse lookupCustomer(
      Long branchId,
      UUID userId,
      String ipAddress,
      String requestedDocType,
      String documentNumber) {

    // 1. Sliding window rate limit and abuse quarantine check
    customerAntiAbuseService.checkRateLimitAndAbuse(branchId, userId, ipAddress);

    if (documentNumber == null || documentNumber.isBlank()) {
      return CustomerLookupResponse.notFound("Número de documento no puede estar vacío");
    }

    String cleanNumber = documentNumber.trim();
    String docType = resolveDocumentType(requestedDocType, cleanNumber);

    if (!isValidDocumentFormat(docType, cleanNumber)) {
      customerAntiAbuseService.recordNegativeLookup(docType, cleanNumber, userId, ipAddress);
      return CustomerLookupResponse.notFound(
          "INVALID_FORMAT",
          "Formato de documento inválido. DNI requiere 8 dígitos y RUC 11 dígitos numéricos.");
    }

    // 2. Check local database (cost S/ 0)
    Optional<Customer> dbCustomer =
        customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber(docType, cleanNumber);
    if (dbCustomer.isPresent()) {
      Customer c = dbCustomer.get();
      customerAntiAbuseService.recordSuccessfulLookup(userId, ipAddress);
      return CustomerLookupResponse.found(
          c.name(),
          c.identityDocumentType(),
          c.identityDocumentNumber(),
          c.phoneNumber(),
          c.address(),
          "LOCAL");
    }

    // 3. Check in-memory L1 cache (to prevent duplicate billable calls during same session)
    String cacheKey = docType + ":" + cleanNumber;
    CacheEntry cached = localCache.get(cacheKey);
    if (cached != null && !cached.isExpired()) {
      customerAntiAbuseService.recordSuccessfulLookup(userId, ipAddress);
      return CustomerLookupResponse.found(
          cached.dto.name(),
          cached.dto.identityDocumentType(),
          cached.dto.identityDocumentNumber(),
          null,
          cached.dto.address(),
          "CACHE");
    }

    // 4. Check negative cache (prevents brute-force / repeated billable calls for non-existent documents)
    if (customerAntiAbuseService.isNegativelyCached(docType, cleanNumber)) {
      return CustomerLookupResponse.notFound(
          "NEGATIVE_CACHE",
          "Documento no encontrado (resultado en caché negativo). Ingrese los datos manualmente.");
    }

    // 5. Check daily billable quota limit (budget protection guard)
    if (!customerAntiAbuseService.tryAcquireDailyQuota()) {
      return CustomerLookupResponse.notFound(
          "QUOTA_EXCEEDED",
          "Límite diario de consultas automáticas a RENIEC/SUNAT alcanzado. Ingrese los datos del cliente manualmente.");
    }

    // 6. Call external Tax API
    Optional<TaxCustomerDto> remoteResult = Optional.empty();
    if ("DNI".equalsIgnoreCase(docType)) {
      remoteResult = taxIdLookupClient.lookupDni(cleanNumber);
    } else if ("RUC".equalsIgnoreCase(docType)) {
      remoteResult = taxIdLookupClient.lookupRuc(cleanNumber);
    }

    if (remoteResult.isPresent()) {
      TaxCustomerDto dto = remoteResult.get();
      putInCache(cacheKey, dto);
      customerAntiAbuseService.recordSuccessfulLookup(userId, ipAddress);
      return CustomerLookupResponse.found(
          dto.name(),
          dto.identityDocumentType(),
          dto.identityDocumentNumber(),
          null,
          dto.address(),
          "THIRD_PARTY");
    }

    // Not found in third-party API -> record negative cache to avoid future billable calls
    customerAntiAbuseService.recordNegativeLookup(docType, cleanNumber, userId, ipAddress);
    return CustomerLookupResponse.notFound(
        "Documento no encontrado o servicio de consulta no disponible temporalmente.");
  }

  private String resolveDocumentType(String requestedDocType, String number) {
    if (requestedDocType != null && !requestedDocType.isBlank()) {
      return requestedDocType.trim().toUpperCase();
    }
    if (number.length() == 8) {
      return "DNI";
    }
    if (number.length() == 11) {
      return "RUC";
    }
    return "UNKNOWN";
  }

  private boolean isValidDocumentFormat(String docType, String number) {
    if ("DNI".equalsIgnoreCase(docType)) {
      return number.matches("^\\d{8}$");
    }
    if ("RUC".equalsIgnoreCase(docType)) {
      return number.matches("^\\d{11}$");
    }
    return number.matches("^[A-Za-z0-9]{4,20}$");
  }

  private void putInCache(String key, TaxCustomerDto dto) {
    if (localCache.size() >= MAX_CACHE_ENTRIES) {
      localCache.clear(); // OOM prevention guard
    }
    localCache.put(key, new CacheEntry(dto, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
  }

  private record CacheEntry(TaxCustomerDto dto, Instant expiry) {
    boolean isExpired() {
      return Instant.now().isAfter(expiry);
    }
  }
}
