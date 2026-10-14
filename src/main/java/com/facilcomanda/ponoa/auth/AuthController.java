package com.facilcomanda.ponoa.auth;

import com.facilcomanda.ponoa.auth.payload.LoginRequest;
import com.facilcomanda.ponoa.auth.payload.RefreshTokenRequest;
import com.facilcomanda.ponoa.auth.payload.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(authService.refresh(request));
  }
}
