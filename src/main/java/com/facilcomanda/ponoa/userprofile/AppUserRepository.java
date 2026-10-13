package com.facilcomanda.ponoa.userprofile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface AppUserRepository extends CrudRepository<AppUser, UUID> {

  Optional<AppUser> findByEmail(String email);

  boolean existsByEmail(String email);

  List<AppUser> findAllByBranchId(Long branchId);

  List<AppUser> findAll();

  @Modifying
  @Query(
      "INSERT INTO app_users (id, first_name, last_name, phone_number) VALUES (:id, :firstName,"
          + " :lastName, :phoneNumber)")
  void insertNewUser(
      @Param("id") UUID id,
      @Param("firstName") String firstName,
      @Param("lastName") String lastName,
      @Param("phoneNumber") String phoneNumber);

  @Modifying
  @Query(
      "INSERT INTO app_users (id, email, first_name, last_name, phone_number, role, branch_id, created_by, is_active, created_at, updated_at) "
          + "VALUES (:id, :email, :firstName, :lastName, :phoneNumber, :role, :branchId, :createdBy, :isActive, :createdAt, :updatedAt)")
  void insertDetailedUser(
      @Param("id") UUID id,
      @Param("email") String email,
      @Param("firstName") String firstName,
      @Param("lastName") String lastName,
      @Param("phoneNumber") String phoneNumber,
      @Param("role") String role,
      @Param("branchId") Long branchId,
      @Param("createdBy") UUID createdBy,
      @Param("isActive") Boolean isActive,
      @Param("createdAt") OffsetDateTime createdAt,
      @Param("updatedAt") OffsetDateTime updatedAt);
}
