package com.facilcomanda.ponoa.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.billing.client.TaxCustomerDto;
import com.facilcomanda.ponoa.billing.client.TaxIdLookupClient;
import com.facilcomanda.ponoa.billing.payload.CustomerLookupResponse;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerLookupServiceTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private TaxIdLookupClient taxIdLookupClient;

  private CustomerLookupService lookupService;

  @BeforeEach
  void setUp() {
    lookupService = new CustomerLookupService(customerRepository, taxIdLookupClient);
  }

  @Test
  @DisplayName("Should return customer from local DB without calling external API")
  void shouldReturnFromLocalDbWithoutExternalApi() {
    Customer local =
        new Customer(
            1L,
            "JUAN PEREZ",
            "DNI",
            "12345678",
            "987654321",
            "Av. Lima 123",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber("DNI", "12345678"))
        .thenReturn(Optional.of(local));

    CustomerLookupResponse response = lookupService.lookupCustomer("DNI", "12345678");

    assertThat(response.found()).isTrue();
    assertThat(response.source()).isEqualTo("LOCAL");
    assertThat(response.name()).isEqualTo("JUAN PEREZ");
    verify(taxIdLookupClient, never()).lookupDni(any());
  }

  @Test
  @DisplayName("Should query external API when document is not in DB, returning preview without saving")
  void shouldQueryExternalApiWhenNotInDb() {
    when(customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber("DNI", "70135060"))
        .thenReturn(Optional.empty());

    when(taxIdLookupClient.lookupDni("70135060"))
        .thenReturn(
            Optional.of(
                new TaxCustomerDto("YOVANA LISBETH MAMANI FAIJO", "DNI", "70135060", null)));

    CustomerLookupResponse response = lookupService.lookupCustomer("DNI", "70135060");

    assertThat(response.found()).isTrue();
    assertThat(response.source()).isEqualTo("THIRD_PARTY");
    assertThat(response.name()).isEqualTo("YOVANA LISBETH MAMANI FAIJO");
    // Verify DB was NEVER written to in preview phase
    verify(customerRepository, never()).save(any());
    verify(customerRepository, never()).upsertCustomer(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should query external API for RUC and return razon social")
  void shouldQueryExternalApiForRuc() {
    when(customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber("RUC", "20605100016"))
        .thenReturn(Optional.empty());

    when(taxIdLookupClient.lookupRuc("20605100016"))
        .thenReturn(
            Optional.of(
                new TaxCustomerDto("RVM MAQUINARIAS S.A.C.", "RUC", "20605100016", "Av. Industrial 456")));

    CustomerLookupResponse response = lookupService.lookupCustomer("RUC", "20605100016");

    assertThat(response.found()).isTrue();
    assertThat(response.source()).isEqualTo("THIRD_PARTY");
    assertThat(response.name()).isEqualTo("RVM MAQUINARIAS S.A.C.");
    assertThat(response.address()).isEqualTo("Av. Industrial 456");
  }

  @Test
  @DisplayName("Should serve consecutive requests from L1 cache to avoid redundant external calls")
  void shouldServeFromCache() {
    when(customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber("DNI", "70135060"))
        .thenReturn(Optional.empty());

    when(taxIdLookupClient.lookupDni("70135060"))
        .thenReturn(
            Optional.of(
                new TaxCustomerDto("YOVANA LISBETH MAMANI FAIJO", "DNI", "70135060", null)));

    // First call -> goes to THIRD_PARTY
    CustomerLookupResponse first = lookupService.lookupCustomer("DNI", "70135060");
    assertThat(first.source()).isEqualTo("THIRD_PARTY");

    // Second call -> should hit CACHE
    CustomerLookupResponse second = lookupService.lookupCustomer("DNI", "70135060");
    assertThat(second.source()).isEqualTo("CACHE");
    assertThat(second.name()).isEqualTo("YOVANA LISBETH MAMANI FAIJO");

    // Third party was only called once
    verify(taxIdLookupClient).lookupDni("70135060");
  }

  @Test
  @DisplayName("Should reject invalid DNI format with less or more than 8 digits")
  void shouldRejectInvalidDniFormat() {
    CustomerLookupResponse response = lookupService.lookupCustomer("DNI", "123");

    assertThat(response.found()).isFalse();
    assertThat(response.allowManualEntry()).isTrue();
    assertThat(response.message()).contains("inválido");
    verify(taxIdLookupClient, never()).lookupDni(any());
  }

  @Test
  @DisplayName("Should handle external API failure gracefully and allow manual entry")
  void shouldHandleExternalApiFailureGracefully() {
    when(customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber("DNI", "88888888"))
        .thenReturn(Optional.empty());

    when(taxIdLookupClient.lookupDni("88888888")).thenReturn(Optional.empty());

    CustomerLookupResponse response = lookupService.lookupCustomer("DNI", "88888888");

    assertThat(response.found()).isFalse();
    assertThat(response.allowManualEntry()).isTrue();
    assertThat(response.message()).contains("no encontrado");
  }

  @Test
  @DisplayName("Should serve from negative cache on repeated non-existent document queries")
  void shouldServeFromNegativeCacheOnRepeatedNonExistentQueries() {
    when(customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber("DNI", "99999999"))
        .thenReturn(Optional.empty());

    when(taxIdLookupClient.lookupDni("99999999")).thenReturn(Optional.empty());

    // First call -> misses DB, calls external API, returns notFound, stores in negative cache
    CustomerLookupResponse first = lookupService.lookupCustomer("DNI", "99999999");
    assertThat(first.found()).isFalse();

    // Second call -> hits negative cache, external API is NOT called again
    CustomerLookupResponse second = lookupService.lookupCustomer("DNI", "99999999");
    assertThat(second.found()).isFalse();
    assertThat(second.source()).isEqualTo("NEGATIVE_CACHE");
    assertThat(second.allowManualEntry()).isTrue();

    // Remote API was called only once
    verify(taxIdLookupClient).lookupDni("99999999");
  }

  @Test
  @DisplayName("Should fallback gracefully without calling external API when daily quota is exceeded")
  void shouldFallbackGracefullyWhenDailyQuotaExceeded() {
    CustomerAntiAbuseService tightQuotaService = new CustomerAntiAbuseService(100, 100, 100, 1);
    CustomerLookupService serviceWithTightQuota =
        new CustomerLookupService(customerRepository, taxIdLookupClient, tightQuotaService);

    when(customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber(any(), any()))
        .thenReturn(Optional.empty());

    when(taxIdLookupClient.lookupDni("70135061"))
        .thenReturn(Optional.of(new TaxCustomerDto("CLIENTE UNO", "DNI", "70135061", null)));

    // First call uses quota (1/1)
    CustomerLookupResponse first = serviceWithTightQuota.lookupCustomer("DNI", "70135061");
    assertThat(first.found()).isTrue();

    // Second call for a new document: daily quota is exhausted
    CustomerLookupResponse second = serviceWithTightQuota.lookupCustomer("DNI", "70135062");
    assertThat(second.found()).isFalse();
    assertThat(second.source()).isEqualTo("QUOTA_EXCEEDED");
    assertThat(second.allowManualEntry()).isTrue();
    assertThat(second.message()).containsIgnoringCase("diario");

    verify(taxIdLookupClient, never()).lookupDni("70135062");
  }
}
