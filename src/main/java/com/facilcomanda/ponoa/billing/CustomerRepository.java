package com.facilcomanda.ponoa.billing;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface CustomerRepository extends CrudRepository<Customer, Long> {

  Optional<Customer> findByIdentityDocumentTypeAndIdentityDocumentNumber(
      String identityDocumentType, String identityDocumentNumber);

  boolean existsByIdentityDocumentTypeAndIdentityDocumentNumber(
      String identityDocumentType, String identityDocumentNumber);

  @Modifying
  @Query(
      "INSERT INTO customers (name, identity_document_type, identity_document_number, phone_number, address, created_at, updated_at) "
          + "VALUES (:name, :type, :number, :phoneNumber, :address, :now, :now) "
          + "ON CONFLICT (identity_document_type, identity_document_number) "
          + "DO UPDATE SET "
          + "  name = EXCLUDED.name, "
          + "  phone_number = COALESCE(EXCLUDED.phone_number, customers.phone_number), "
          + "  address = COALESCE(EXCLUDED.address, customers.address), "
          + "  updated_at = EXCLUDED.updated_at")
  void upsertCustomer(
      @Param("name") String name,
      @Param("type") String type,
      @Param("number") String number,
      @Param("phoneNumber") String phoneNumber,
      @Param("address") String address,
      @Param("now") OffsetDateTime now);
}
