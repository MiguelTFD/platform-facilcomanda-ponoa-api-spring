package com.facilcomanda.ponoa.billing;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface CustomerRepository extends CrudRepository<Customer, Long> {

  @Query("SELECT * FROM customers WHERE is_active = TRUE ORDER BY id DESC LIMIT 200")
  java.util.List<Customer> findAllActive();

  @Query(
      "SELECT * FROM customers "
          + "WHERE is_active = TRUE AND ("
          + "  LOWER(name) LIKE LOWER(CONCAT('%', :query, '%')) OR "
          + "  identity_document_number LIKE CONCAT('%', :query, '%')"
          + ") ORDER BY id DESC LIMIT 100")
  java.util.List<Customer> searchActive(@Param("query") String query);

  @Query("SELECT * FROM customers WHERE id = :id AND is_active = TRUE")
  Optional<Customer> findActiveById(@Param("id") Long id);

  @Query(
      "SELECT * FROM customers "
          + "WHERE identity_document_type = :type AND identity_document_number = :number AND is_active = TRUE")
  Optional<Customer> findByIdentityDocumentTypeAndIdentityDocumentNumber(
      @Param("type") String identityDocumentType, @Param("number") String identityDocumentNumber);

  @Query(
      "SELECT COUNT(*) > 0 FROM customers "
          + "WHERE identity_document_type = :type AND identity_document_number = :number AND is_active = TRUE")
  boolean existsByIdentityDocumentTypeAndIdentityDocumentNumber(
      @Param("type") String identityDocumentType, @Param("number") String identityDocumentNumber);

  @Modifying
  @Query(
      "INSERT INTO customers (name, identity_document_type, identity_document_number, phone_number, address, is_active, created_at, updated_at) "
          + "VALUES (:name, :type, :number, :phoneNumber, :address, TRUE, :now, :now) "
          + "ON CONFLICT (identity_document_type, identity_document_number) "
          + "DO UPDATE SET "
          + "  name = EXCLUDED.name, "
          + "  phone_number = COALESCE(EXCLUDED.phone_number, customers.phone_number), "
          + "  address = COALESCE(EXCLUDED.address, customers.address), "
          + "  is_active = TRUE, "
          + "  deleted_at = NULL, "
          + "  updated_at = EXCLUDED.updated_at")
  void upsertCustomer(
      @Param("name") String name,
      @Param("type") String type,
      @Param("number") String number,
      @Param("phoneNumber") String phoneNumber,
      @Param("address") String address,
      @Param("now") OffsetDateTime now);

  @Modifying
  @Query(
      "UPDATE customers SET is_active = FALSE, deleted_at = NOW(), updated_at = NOW() "
          + "WHERE id = :id AND is_active = TRUE")
  int softDeleteCustomer(@Param("id") Long id);
}
