package com.facilcomanda.ponoa.global.security;

import com.facilcomanda.ponoa.global.exception.IdentityProviderUnavailableException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class KeycloakAdminClient {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

  private final RestClient restClient;
  private final String serverUrl;
  private final String realm;
  private final String adminUsername;
  private final String adminPassword;
  private final String adminRealm;
  private final String adminClientId;

  private volatile String cachedToken;
  private volatile Instant tokenExpiry = Instant.MIN;

  public KeycloakAdminClient(
      @Value("${app.keycloak.server-url:http://localhost:8080}") String serverUrl,
      @Value("${app.keycloak.realm:fcp-realm}") String realm,
      @Value("${app.keycloak.admin-username:admin}") String adminUsername,
      @Value("${app.keycloak.admin-password:admin}") String adminPassword,
      @Value("${app.keycloak.admin-realm:master}") String adminRealm,
      @Value("${app.keycloak.admin-client-id:admin-cli}") String adminClientId) {

    this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    this.realm = realm;
    this.adminUsername = adminUsername;
    this.adminPassword = adminPassword;
    this.adminRealm = adminRealm;
    this.adminClientId = adminClientId;

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(4000));
    factory.setReadTimeout(Duration.ofMillis(6000));

    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  public UUID createUserWithRole(
      String email,
      String password,
      String firstName,
      String lastName,
      String roleName) {
    return createUserWithRole(email, password, firstName, lastName, roleName, null);
  }

  public UUID createUserWithRole(
      String email,
      String password,
      String firstName,
      String lastName,
      String roleName,
      Long branchId) {

    String adminToken = getAdminToken();

    Map<String, Object> credentialMap =
        Map.of(
            "type", "password",
            "value", password,
            "temporary", false);

    Map<String, Object> userPayload = new HashMap<>();
    userPayload.put("username", email);
    userPayload.put("email", email);
    userPayload.put("firstName", firstName);
    userPayload.put("lastName", lastName);
    userPayload.put("enabled", true);
    userPayload.put("emailVerified", true);
    userPayload.put("credentials", List.of(credentialMap));

    if (branchId != null) {
      userPayload.put("attributes", Map.of("branch_id", List.of(String.valueOf(branchId))));
    }

    String usersUri = serverUrl + "/admin/realms/" + realm + "/users";

    ResponseEntity<Void> response;
    try {
      response =
          restClient
              .post()
              .uri(usersUri)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(userPayload)
              .retrieve()
              .toBodilessEntity();
    } catch (HttpClientErrorException.Conflict conflictEx) {
      log.warn("Keycloak user with email '{}' already exists", email);
      throw new DuplicateKeyException("User with email " + email + " already exists in Keycloak.");
    } catch (Exception ex) {
      log.error("Failed to create user in Keycloak for email: {}", email, ex);
      throw new IdentityProviderUnavailableException("Failed to create identity in Keycloak: " + ex.getMessage(), ex);
    }

    URI location = response.getHeaders().getLocation();
    if (location == null) {
      throw new IllegalStateException("Keycloak did not return Location header for created user: " + email);
    }

    String path = location.getPath();
    UUID userId = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));

    if (roleName != null && !roleName.isBlank()) {
      assignRealmRole(userId, roleName, adminToken);
    }

    log.info("Successfully provisioned Keycloak user: {} [id={}, role={}, branchId={}]", email, userId, roleName, branchId);
    return userId;
  }

  public void updateUserBranch(
      UUID userId,
      String email,
      String firstName,
      String lastName,
      Long branchId) {
    if (userId == null) {
      return;
    }
    try {
      String adminToken = getAdminToken();
      String userUri = serverUrl + "/admin/realms/" + realm + "/users/" + userId;

      Map<String, Object> userPayload = new HashMap<>();
      if (email != null) {
        userPayload.put("email", email);
        userPayload.put("username", email);
      }
      if (firstName != null) {
        userPayload.put("firstName", firstName);
      }
      if (lastName != null) {
        userPayload.put("lastName", lastName);
      }
      userPayload.put("enabled", true);

      if (branchId != null) {
        userPayload.put("attributes", Map.of("branch_id", List.of(String.valueOf(branchId))));
      } else {
        userPayload.put("attributes", Map.of("branch_id", List.of()));
      }

      restClient
          .put()
          .uri(userUri)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(userPayload)
          .retrieve()
          .toBodilessEntity();

      log.info("Successfully updated Keycloak user branch: {} [id={}, branchId={}]", email, userId, branchId);
    } catch (Exception ex) {
      log.warn("Could not update Keycloak user branch for {}: {}", userId, ex.getMessage());
    }
  }

  public void updateUserBranches(
      UUID userId,
      String email,
      String firstName,
      String lastName,
      java.util.Collection<Long> branchIds) {
    if (userId == null) {
      return;
    }
    try {
      String adminToken = getAdminToken();
      String userUri = serverUrl + "/admin/realms/" + realm + "/users/" + userId;

      Map<String, Object> userPayload = new HashMap<>();
      if (email != null) {
        userPayload.put("email", email);
        userPayload.put("username", email);
      }
      if (firstName != null) {
        userPayload.put("firstName", firstName);
      }
      if (lastName != null) {
        userPayload.put("lastName", lastName);
      }
      userPayload.put("enabled", true);

      if (branchIds != null && !branchIds.isEmpty()) {
        List<String> branchStrings = branchIds.stream().map(String::valueOf).toList();
        userPayload.put(
            "attributes",
            Map.of(
                "branch_id", List.of(branchStrings.get(0)),
                "branches", branchStrings));
      } else {
        userPayload.put(
            "attributes",
            Map.of(
                "branch_id", List.of(),
                "branches", List.of()));
      }

      restClient
          .put()
          .uri(userUri)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(userPayload)
          .retrieve()
          .toBodilessEntity();

      log.info(
          "Successfully updated Keycloak user branches: {} [id={}, branchIds={}]",
          email,
          userId,
          branchIds);
    } catch (Exception ex) {
      log.warn("Could not update Keycloak user branches for {}: {}", userId, ex.getMessage());
    }
  }

  public void resetUserPassword(UUID userId, String newPassword) {
    if (userId == null || newPassword == null || newPassword.isBlank()) {
      return;
    }
    String adminToken = getAdminToken();
    String resetPasswordUri =
        serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/reset-password";

    Map<String, Object> credentialPayload =
        Map.of(
            "type", "password",
            "value", newPassword,
            "temporary", false);

    try {
      restClient
          .put()
          .uri(resetPasswordUri)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(credentialPayload)
          .retrieve()
          .toBodilessEntity();
      log.info("Successfully updated password in Keycloak for user {}", userId);
    } catch (Exception ex) {
      log.error("Failed to reset password in Keycloak for user {}", userId, ex);
      throw new IdentityProviderUnavailableException(
          "Failed to update user password in Keycloak: " + ex.getMessage(), ex);
    }
  }

  public void assignRealmRoles(UUID userId, java.util.Collection<String> roleNames) {
    if (userId == null || roleNames == null || roleNames.isEmpty()) {
      return;
    }
    try {
      String adminToken = getAdminToken();
      for (String roleName : roleNames) {
        if (roleName != null && !roleName.isBlank()) {
          assignRealmRole(userId, roleName, adminToken);
        }
      }
    } catch (Exception ex) {
      log.warn("Could not assign realm roles {} to user {}: {}", roleNames, userId, ex.getMessage());
    }
  }

  public void assignRealmRole(UUID userId, String roleName, String adminToken) {
    if (adminToken == null) {
      adminToken = getAdminToken();
    }

    String roleUri = serverUrl + "/admin/realms/" + realm + "/roles/" + roleName;
    KeycloakRoleRepresentation role;
    try {
      role =
          restClient
              .get()
              .uri(roleUri)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(KeycloakRoleRepresentation.class);
    } catch (HttpClientErrorException.NotFound notFoundEx) {
      log.info("Realm role '{}' does not exist in Keycloak. Auto-creating role...", roleName);
      ensureRealmRoleCreated(roleName, adminToken);
      try {
        role =
            restClient
                .get()
                .uri(roleUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(KeycloakRoleRepresentation.class);
      } catch (Exception ex) {
        log.error("Failed to retrieve realm role '{}' after auto-creation", roleName, ex);
        throw new IdentityProviderUnavailableException("Realm role '" + roleName + "' could not be created/retrieved in Keycloak.", ex);
      }
    } catch (Exception ex) {
      log.error("Failed to retrieve realm role '{}' from Keycloak", roleName, ex);
      throw new IdentityProviderUnavailableException("Realm role '" + roleName + "' does not exist in Keycloak.", ex);
    }

    if (role == null || role.id() == null) {
      throw new IllegalStateException("Realm role '" + roleName + "' is invalid in Keycloak.");
    }

    String roleMappingUri =
        serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";

    List<KeycloakRoleRepresentation> roleList = List.of(role);

    try {
      restClient
          .post()
          .uri(roleMappingUri)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(roleList)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception ex) {
      log.error("Failed to assign role '{}' to user {}", roleName, userId, ex);
      throw new IdentityProviderUnavailableException("Failed to assign role in Keycloak: " + ex.getMessage(), ex);
    }
  }

  public void ensureRealmRoleCreated(String roleName, String adminToken) {
    if (adminToken == null) {
      adminToken = getAdminToken();
    }
    String rolesUri = serverUrl + "/admin/realms/" + realm + "/roles";
    try {
      restClient
          .post()
          .uri(rolesUri)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("name", roleName))
          .retrieve()
          .toBodilessEntity();
      log.info("Successfully auto-created realm role '{}' in Keycloak", roleName);
    } catch (HttpClientErrorException.Conflict conflict) {
      // Role already exists
    } catch (Exception e) {
      log.warn("Could not auto-create realm role '{}' in Keycloak: {}", roleName, e.getMessage());
    }
  }

  public void deleteUser(UUID userId) {
    if (userId == null) {
      return;
    }
    try {
      String adminToken = getAdminToken();
      String deleteUri = serverUrl + "/admin/realms/" + realm + "/users/" + userId;
      restClient
          .delete()
          .uri(deleteUri)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
          .retrieve()
          .toBodilessEntity();
      log.info("Deleted Keycloak user: {}", userId);
    } catch (HttpClientErrorException.NotFound ignored) {
      // Already gone
    } catch (Exception ex) {
      log.warn("Could not delete user {} from Keycloak during compensating rollback: {}", userId, ex.getMessage());
    }
  }

  public synchronized void invalidateCachedToken() {
    this.cachedToken = null;
    this.tokenExpiry = Instant.MIN;
  }

  private synchronized String getAdminToken() {
    if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(15))) {
      return cachedToken;
    }

    String tokenUri = serverUrl + "/realms/" + adminRealm + "/protocol/openid-connect/token";

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "password");
    formData.add("client_id", adminClientId);
    formData.add("username", adminUsername);
    formData.add("password", adminPassword);

    try {
      AdminTokenResponse tokenResponse =
          restClient
              .post()
              .uri(tokenUri)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(formData)
              .retrieve()
              .body(AdminTokenResponse.class);

      if (tokenResponse == null || tokenResponse.accessToken() == null) {
        throw new IllegalStateException("Received empty admin token from Keycloak.");
      }

      this.cachedToken = tokenResponse.accessToken();
      long expiresIn = tokenResponse.expiresIn() != null ? tokenResponse.expiresIn() : 60L;
      this.tokenExpiry = Instant.now().plusSeconds(expiresIn);
      return cachedToken;
    } catch (Exception ex) {
      log.error("Failed to obtain admin token from Keycloak at {}", tokenUri, ex);
      throw new IdentityProviderUnavailableException("Could not authenticate with Keycloak Admin: " + ex.getMessage(), ex);
    }
  }

  public record KeycloakRoleRepresentation(
      String id,
      String name,
      String description,
      Boolean composite,
      Boolean clientRole,
      String containerId) {}

  private record AdminTokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("token_type") String tokenType) {}
}
