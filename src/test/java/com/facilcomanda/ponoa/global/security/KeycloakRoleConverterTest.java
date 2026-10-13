package com.facilcomanda.ponoa.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRoleConverterTest {

  private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

  @Test
  void shouldExtractRolesAndAppendPrefixSuccessfully() {
    Jwt jwtMock = mock(Jwt.class);
    Map<String, Object> realmAccess = Map.of("roles", List.of("admin", "customer"));

    when(jwtMock.getClaimAsMap("realm_access")).thenReturn(realmAccess);

    Collection<GrantedAuthority> authorities = converter.convert(jwtMock);

    assertEquals(2, authorities.size(), "If this is correct,a 2 role's will be returned");
    assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_admin")));
    assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_customer")));
  }

  @Test
  void shouldReturnEmptyListWhenRealmAccessIsNull() {
    Jwt jwtMock = mock(Jwt.class);
    when(jwtMock.getClaimAsMap("realm_access")).thenReturn(null);

    Collection<GrantedAuthority> authorities = converter.convert(jwtMock);

    assertTrue(
        authorities.isEmpty(),
        "If this is correct, an empty list will be returned to prevent a NullPointerException");
  }

  @Test
  void shouldReturnEmptyListWhenRolesArrayIsMissing() {
    Jwt jwtMock = mock(Jwt.class);
    Map<String, Object> emptyRealmAccess = Map.of();
    when(jwtMock.getClaimAsMap("realm_access")).thenReturn(emptyRealmAccess);

    Collection<GrantedAuthority> authorities = converter.convert(jwtMock);

    assertTrue(
        authorities.isEmpty(),
        "If this is correct, It should return an empty list if there is no role mapping.");
  }
}
