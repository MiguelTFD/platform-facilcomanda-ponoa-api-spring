package com.facilcomanda.ponoa.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

  @Test
  @DisplayName("Should configure CORS with wildcard pattern and credentials support")
  void shouldConfigureCorsWithWildcardPattern() {
    SecurityConfig securityConfig = new SecurityConfig();
    ReflectionTestUtils.setField(securityConfig, "allowedOrigins", "*");

    CorsConfigurationSource source = securityConfig.corsConfigurationSource();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/orders");

    CorsConfiguration config = source.getCorsConfiguration(request);

    assertThat(config).isNotNull();
    assertThat(config.getAllowedOriginPatterns()).contains("*");
    assertThat(config.getAllowCredentials()).isTrue();
    assertThat(config.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    assertThat(config.getAllowedHeaders())
        .contains("Authorization", "Content-Type", "X-Branch-Id", "Idempotency-Key");
    assertThat(config.getExposedHeaders()).contains("Location", "Retry-After", "X-Branch-Id");
    assertThat(config.getMaxAge()).isEqualTo(3600L);
  }

  @Test
  @DisplayName("Should configure CORS with specific allowed origins list")
  void shouldConfigureCorsWithSpecificOrigins() {
    SecurityConfig securityConfig = new SecurityConfig();
    ReflectionTestUtils.setField(
        securityConfig,
        "allowedOrigins",
        "https://app.facilcomanda.com, http://localhost:3000, http://localhost:5173");

    CorsConfigurationSource source = securityConfig.corsConfigurationSource();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/invoices");

    CorsConfiguration config = source.getCorsConfiguration(request);

    assertThat(config).isNotNull();
    assertThat(config.getAllowedOrigins())
        .containsExactlyInAnyOrder(
            "https://app.facilcomanda.com",
            "http://localhost:3000",
            "http://localhost:5173");
    assertThat(config.getAllowCredentials()).isTrue();
  }

  @Test
  @DisplayName("Should create JwtAuthenticationConverter with Keycloak converter")
  void shouldCreateJwtAuthenticationConverter() {
    SecurityConfig securityConfig = new SecurityConfig();
    assertThat(securityConfig.jwtAuthenticationConverter()).isNotNull();
  }
}
