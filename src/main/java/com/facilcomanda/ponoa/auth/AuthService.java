package com.facilcomanda.ponoa.auth;

import com.facilcomanda.ponoa.auth.payload.LoginRequest;
import com.facilcomanda.ponoa.auth.payload.RefreshTokenRequest;
import com.facilcomanda.ponoa.auth.payload.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final RestClient restClient;
  private final String tokenUrl;
  private final String clientId;
  private final String clientSecret;

  public AuthService(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080/realms/fcp-realm}")
          String issuerUri,
      @Value("${app.auth.client-id:fcp-api}") String clientId,
      @Value("${app.auth.client-secret:}") String clientSecret) {

    this.tokenUrl = issuerUri + "/protocol/openid-connect/token";
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.restClient = RestClient.builder().build();
  }

  public TokenResponse login(LoginRequest request) {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "password");
    formData.add("client_id", clientId);
    if (clientSecret != null && !clientSecret.isBlank()) {
      formData.add("client_secret", clientSecret.trim());
    }
    formData.add("username", request.email().trim());
    formData.add("password", request.password());

    try {
      return restClient
          .post()
          .uri(tokenUrl)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(formData)
          .retrieve()
          .body(TokenResponse.class);
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest e) {
      log.warn("Authentication failed for user: {}", request.email());
      throw new BadCredentialsException("Credenciales inválidas. Por favor verifique su correo y contraseña.");
    } catch (Exception e) {
      log.error("Error communicating with Keycloak token endpoint: {}", e.getMessage(), e);
      throw new IllegalStateException("Servicio de autenticación no disponible temporalmente. Intente nuevamente.");
    }
  }

  public TokenResponse refresh(RefreshTokenRequest request) {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "refresh_token");
    formData.add("client_id", clientId);
    if (clientSecret != null && !clientSecret.isBlank()) {
      formData.add("client_secret", clientSecret.trim());
    }
    formData.add("refresh_token", request.refreshToken());

    try {
      return restClient
          .post()
          .uri(tokenUrl)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(formData)
          .retrieve()
          .body(TokenResponse.class);
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest e) {
      log.warn("Token refresh failed");
      throw new BadCredentialsException("El refresh token ha expirado o es inválido. Inicie sesión nuevamente.");
    } catch (Exception e) {
      log.error("Error refreshing token with Keycloak: {}", e.getMessage(), e);
      throw new IllegalStateException("Servicio de autenticación no disponible temporalmente. Intente nuevamente.");
    }
  }
}
