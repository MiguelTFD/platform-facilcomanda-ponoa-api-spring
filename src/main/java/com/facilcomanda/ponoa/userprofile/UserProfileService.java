package com.facilcomanda.ponoa.userprofile;

import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserProfileService {

  private final AppUserRepository userRepository;

  UserProfileService(AppUserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional
  public void synchronizeUserFromToken(UUID keycloakId, String firstName, String lastName) {
    boolean exists = userRepository.existsById(keycloakId);

    if (!exists) {
      userRepository.insertNewUser(keycloakId, firstName, lastName, null);
    }
  }

  public AppUser getUserProfile(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException("The user profile is not registered in the system."));
  }
}
