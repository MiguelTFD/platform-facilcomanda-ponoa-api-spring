package com.facilcomanda.ponoa.billing;

import com.facilcomanda.ponoa.billing.payload.CustomerPayload;
import com.facilcomanda.ponoa.billing.payload.CustomerResponse;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private final CustomerRepository customerRepository;

  public CustomerService(CustomerRepository customerRepository) {
    this.customerRepository = customerRepository;
  }

  @Transactional(readOnly = true)
  public List<CustomerResponse> listCustomers(String query) {
    List<Customer> customers;
    if (query != null && !query.isBlank()) {
      customers = customerRepository.searchActive(query.trim());
    } else {
      customers = customerRepository.findAllActive();
    }

    return customers.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public CustomerResponse getCustomerById(Long id) {
    return customerRepository
        .findActiveById(id)
        .map(this::toResponse)
        .orElseThrow(
            () -> new ResourceNotFoundException("Customer with id " + id + " not found."));
  }

  @Transactional
  public CustomerResponse createOrUpdateCustomer(CustomerPayload request) {
    String docType = request.identityDocumentType().trim().toUpperCase();
    String docNum = request.identityDocumentNumber().trim();
    String name = request.name().trim();
    String phone = request.phoneNumber() != null ? request.phoneNumber().trim() : null;
    String address = request.address() != null ? request.address().trim() : null;
    OffsetDateTime now = OffsetDateTime.now();

    customerRepository.upsertCustomer(name, docType, docNum, phone, address, now);

    Customer saved =
        customerRepository
            .findByIdentityDocumentTypeAndIdentityDocumentNumber(docType, docNum)
            .orElseThrow(
                () -> new IllegalStateException("Failed to retrieve customer after upsert."));

    return toResponse(saved);
  }

  private CustomerResponse toResponse(Customer customer) {
    return new CustomerResponse(
        customer.id(),
        customer.name(),
        customer.identityDocumentType(),
        customer.identityDocumentNumber(),
        customer.phoneNumber(),
        customer.address(),
        customer.isActive(),
        customer.createdAt(),
        customer.updatedAt());
  }
}
