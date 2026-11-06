package com.facilcomanda.ponoa.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.billing.payload.CustomerPayload;
import com.facilcomanda.ponoa.billing.payload.CustomerResponse;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  @Mock
  private CustomerRepository customerRepository;

  private CustomerService customerService;

  @BeforeEach
  void setUp() {
    customerService = new CustomerService(customerRepository);
  }

  @Test
  void listCustomers_whenQueryIsNull_shouldReturnAllActive() {
    Customer c1 =
        new Customer(1L, "Juan Perez", "DNI", "12345678", "999888777", "Av Lima 123", OffsetDateTime.now(), OffsetDateTime.now());
    when(customerRepository.findAllActive()).thenReturn(List.of(c1));

    List<CustomerResponse> result = customerService.listCustomers(null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("Juan Perez");
    assertThat(result.get(0).identityDocumentNumber()).isEqualTo("12345678");
  }

  @Test
  void listCustomers_whenQueryIsProvided_shouldSearchActive() {
    Customer c1 =
        new Customer(2L, "Acme SAC", "RUC", "20123456789", "999111222", "Av Corp 456", OffsetDateTime.now(), OffsetDateTime.now());
    when(customerRepository.searchActive("Acme")).thenReturn(List.of(c1));

    List<CustomerResponse> result = customerService.listCustomers("Acme");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("Acme SAC");
    verify(customerRepository).searchActive("Acme");
  }

  @Test
  void getCustomerById_whenFound_shouldReturnCustomer() {
    Customer c =
        new Customer(5L, "Maria Lopez", "DNI", "87654321", null, null, OffsetDateTime.now(), OffsetDateTime.now());
    when(customerRepository.findActiveById(5L)).thenReturn(Optional.of(c));

    CustomerResponse result = customerService.getCustomerById(5L);

    assertThat(result.id()).isEqualTo(5L);
    assertThat(result.name()).isEqualTo("Maria Lopez");
  }

  @Test
  void getCustomerById_whenNotFound_shouldThrowResourceNotFoundException() {
    when(customerRepository.findActiveById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.getCustomerById(99L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Customer with id 99 not found.");
  }

  @Test
  void createOrUpdateCustomer_shouldUpsertAndReturnCustomer() {
    CustomerPayload payload =
        new CustomerPayload("Carlos Diaz", "DNI", "45678901", "987654321", "Calle Real 789");
    Customer saved =
        new Customer(10L, "Carlos Diaz", "DNI", "45678901", "987654321", "Calle Real 789", OffsetDateTime.now(), OffsetDateTime.now());

    when(customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber("DNI", "45678901"))
        .thenReturn(Optional.of(saved));

    CustomerResponse response = customerService.createOrUpdateCustomer(payload);

    assertThat(response.id()).isEqualTo(10L);
    assertThat(response.name()).isEqualTo("Carlos Diaz");
    verify(customerRepository)
        .upsertCustomer(
            eq("Carlos Diaz"),
            eq("DNI"),
            eq("45678901"),
            eq("987654321"),
            eq("Calle Real 789"),
            any(OffsetDateTime.class));
  }
}
