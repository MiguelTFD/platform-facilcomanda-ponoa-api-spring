package com.facilcomanda.ponoa.userprofile;

import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface AppUserRepository extends CrudRepository<AppUser, UUID> {
  @Modifying
  @Query(
      "INSERT INTO app_users (id, first_name, last_name, phone_number) VALUES (:id, :firstName,"
          + " :lastName, :phoneNumber)")
  void insertNewUser(
      @Param("id") UUID id,
      @Param("firstName") String firstName,
      @Param("lastName") String lastName,
      @Param("phoneNumber") String phoneNumber);
}
