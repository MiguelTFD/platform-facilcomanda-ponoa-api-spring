package com.facilcomanda.ponoa.userprofile;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
class UserProfileController {

  private final UserProfileService userProfileService;

  UserProfileController(UserProfileService userProfileService) {
    this.userProfileService = userProfileService;
  }

  @PostMapping("/sync")
  public ResponseEntity<Void> synchronizeProfile(@AuthenticationPrincipal Jwt jwt) {
    UUID keycloakId = UUID.fromString(jwt.getSubject());

    String firstName = jwt.getClaimAsString("given_name");
    String lastName = jwt.getClaimAsString("family_name");

    userProfileService.synchronizeUserFromToken(keycloakId, firstName, lastName);

    return ResponseEntity.ok().build();
  }

  @GetMapping("/me")
  public ResponseEntity<AppUser> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
    UUID keycloakId = UUID.fromString(jwt.getSubject());

    AppUser profile = userProfileService.getUserProfile(keycloakId);

    return ResponseEntity.ok(profile);
  }
}
