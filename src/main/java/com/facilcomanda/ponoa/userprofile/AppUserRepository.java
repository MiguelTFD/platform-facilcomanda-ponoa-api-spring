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

  @Query(
      """
      SELECT DISTINCT u.* FROM app_users u
      LEFT JOIN app_user_branches aub ON aub.app_user_id = u.id
      WHERE u.branch_id = :branchId OR aub.branch_id = :branchId
      ORDER BY u.created_at DESC LIMIT 200
      """)
  List<AppUser> findAllByBranchId(@Param("branchId") Long branchId);

  @Query("SELECT * FROM app_users ORDER BY created_at DESC LIMIT 200")
  List<AppUser> findAll();

  @Query("SELECT branch_id FROM app_user_branches WHERE app_user_id = :userId ORDER BY branch_id")
  List<Long> findBranchIdsByUserId(@Param("userId") UUID userId);

  @Modifying
  @Query("DELETE FROM app_user_branches WHERE app_user_id = :userId")
  void deleteUserBranches(@Param("userId") UUID userId);

  @Modifying
  @Query(
      "INSERT INTO app_user_branches (app_user_id, branch_id) VALUES (:userId, :branchId) ON CONFLICT DO NOTHING")
  void insertUserBranch(@Param("userId") UUID userId, @Param("branchId") Long branchId);

  @Query("SELECT role FROM app_user_roles WHERE app_user_id = :userId ORDER BY role")
  List<String> findRolesByUserId(@Param("userId") UUID userId);

  @Modifying
  @Query("DELETE FROM app_user_roles WHERE app_user_id = :userId")
  void deleteUserRoles(@Param("userId") UUID userId);

  @Modifying
  @Query(
      "INSERT INTO app_user_roles (app_user_id, role) VALUES (:userId, :role) ON CONFLICT DO NOTHING")
  void insertUserRole(@Param("userId") UUID userId, @Param("role") String role);

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

  @Modifying
  @Query(
      """
      UPDATE app_users 
      SET first_name = :firstName, 
          last_name = :lastName, 
          phone_number = :phoneNumber, 
          role = :role, 
          branch_id = :branchId, 
          is_active = :isActive, 
          updated_at = NOW() 
      WHERE id = :id
      """)
  int updateUserDetails(
      @Param("id") UUID id,
      @Param("firstName") String firstName,
      @Param("lastName") String lastName,
      @Param("phoneNumber") String phoneNumber,
      @Param("role") String role,
      @Param("branchId") Long branchId,
      @Param("isActive") Boolean isActive);

  @Modifying
  @Query("UPDATE app_users SET is_active = FALSE, updated_at = NOW() WHERE id = :id AND is_active = TRUE")
  int deactivateUser(@Param("id") UUID id);
}
