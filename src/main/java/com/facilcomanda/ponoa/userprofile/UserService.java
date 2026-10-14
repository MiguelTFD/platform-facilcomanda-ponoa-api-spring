package com.facilcomanda.ponoa.userprofile;

import com.facilcomanda.ponoa.branch.BranchProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.global.security.UserBranchResolver;
import com.facilcomanda.ponoa.userprofile.payload.CreateAdminUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.CreateCompanyUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.CreateStaffUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.UserResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserService implements UserBranchResolver {

  private final AppUserRepository userRepository;
  private final BranchProvider branchProvider;

  UserService(AppUserRepository userRepository, BranchProvider branchProvider) {
    this.userRepository = userRepository;
    this.branchProvider = branchProvider;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Long> findBranchIdForUser(UUID userId) {
    return userRepository.findById(userId).map(AppUser::branchId);
  }

  @Transactional
  public UUID createCompanyUser(CreateCompanyUserRequest request) {
    if (request.email() != null && userRepository.existsByEmail(request.email())) {
      throw new DuplicateKeyException("User with email " + request.email() + " already exists.");
    }

    UUID userId = request.id() != null ? request.id() : UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();

    userRepository.insertDetailedUser(
        userId,
        request.email(),
        request.firstName(),
        request.lastName(),
        request.phoneNumber(),
        AppUserRole.EMPRESA.name(),
        null,
        null,
        true,
        now,
        now);

    return userId;
  }

  @Transactional
  public UUID createAdminUser(UUID callerId, CreateAdminUserRequest request) {
    if (!branchProvider.existsBranch(request.branchId())) {
      throw new ResourceNotFoundException("Branch with id " + request.branchId() + " does not exist.");
    }

    if (request.email() != null && userRepository.existsByEmail(request.email())) {
      throw new DuplicateKeyException("User with email " + request.email() + " already exists.");
    }

    UUID userId = request.id() != null ? request.id() : UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();

    userRepository.insertDetailedUser(
        userId,
        request.email(),
        request.firstName(),
        request.lastName(),
        request.phoneNumber(),
        AppUserRole.ADMINISTRADOR.name(),
        request.branchId(),
        callerId,
        true,
        now,
        now);

    return userId;
  }

  @Transactional
  public UUID createStaffUser(
      UUID callerId,
      boolean isCallerEmpresa,
      Long tokenBranchId,
      CreateStaffUserRequest request) {

    if (request.role() != AppUserRole.COCINERO
        && request.role() != AppUserRole.CAJERO
        && request.role() != AppUserRole.MESERO) {
      throw new IllegalArgumentException(
          "Staff creation only permits roles: COCINERO, CAJERO, MESERO. Provided: " + request.role());
    }

    Long targetBranchId;
    if (isCallerEmpresa) {
      if (request.branchId() == null) {
        throw new IllegalArgumentException("branchId must be provided when company creates a staff member.");
      }
      if (!branchProvider.existsBranch(request.branchId())) {
        throw new ResourceNotFoundException("Branch with id " + request.branchId() + " does not exist.");
      }
      targetBranchId = request.branchId();
    } else {
      // Caller is ADMINISTRADOR: resolve branch from DB, fallback to token claim
      targetBranchId = userRepository
          .findById(callerId)
          .map(AppUser::branchId)
          .orElse(tokenBranchId);

      if (targetBranchId == null) {
        throw new AccessDeniedException("Administrator is not assigned to any branch.");
      }
    }

    if (request.email() != null && userRepository.existsByEmail(request.email())) {
      throw new DuplicateKeyException("User with email " + request.email() + " already exists.");
    }

    UUID userId = request.id() != null ? request.id() : UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();

    userRepository.insertDetailedUser(
        userId,
        request.email(),
        request.firstName(),
        request.lastName(),
        request.phoneNumber(),
        request.role().name(),
        targetBranchId,
        callerId,
        true,
        now,
        now);

    return userId;
  }

  @Transactional(readOnly = true)
  public List<UserResponse> listUsers(
      UUID callerId,
      boolean isCallerEmpresa,
      Long tokenBranchId,
      Long filterBranchId) {

    if (isCallerEmpresa) {
      if (filterBranchId != null) {
        return userRepository.findAllByBranchId(filterBranchId).stream()
            .map(this::toResponse)
            .toList();
      }
      return userRepository.findAll().stream()
          .map(this::toResponse)
          .toList();
    }

    Long callerBranchId = userRepository
        .findById(callerId)
        .map(AppUser::branchId)
        .orElse(tokenBranchId);

    if (callerBranchId == null) {
      throw new AccessDeniedException("User is not assigned to any branch.");
    }

    return userRepository.findAllByBranchId(callerBranchId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public UserResponse getUserById(
      UUID callerId,
      boolean isCallerEmpresa,
      Long tokenBranchId,
      UUID targetUserId) {

    AppUser user = userRepository
        .findById(targetUserId)
        .orElseThrow(() -> new ResourceNotFoundException("User with id " + targetUserId + " not found."));

    if (!isCallerEmpresa) {
      Long callerBranchId = userRepository
          .findById(callerId)
          .map(AppUser::branchId)
          .orElse(tokenBranchId);

      boolean isSelf = callerId.equals(targetUserId);
      boolean isSameBranch = callerBranchId != null && callerBranchId.equals(user.branchId());

      if (!isSelf && !isSameBranch) {
        throw new AccessDeniedException("You do not have permission to view this user.");
      }
    }

    return toResponse(user);
  }

  @Transactional
  public void updateUser(
      UUID callerId,
      boolean isCallerEmpresa,
      Long tokenBranchId,
      UUID targetUserId,
      com.facilcomanda.ponoa.userprofile.payload.UpdateUserRequest request) {

    AppUser user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(
                () -> new ResourceNotFoundException("User with id " + targetUserId + " not found."));

    Long targetBranchId = user.branchId();

    if (!isCallerEmpresa) {
      Long callerBranchId =
          userRepository
              .findById(callerId)
              .map(AppUser::branchId)
              .orElse(tokenBranchId);

      boolean isSameBranch = callerBranchId != null && callerBranchId.equals(user.branchId());
      if (!isSameBranch) {
        throw new AccessDeniedException("You do not have permission to modify this user.");
      }

      if (request.role() == AppUserRole.EMPRESA) {
        throw new AccessDeniedException("Administrators cannot promote users to EMPRESA.");
      }

      if (request.branchId() != null && !request.branchId().equals(user.branchId())) {
        throw new AccessDeniedException("Administrators cannot reassign users to other branches.");
      }
    } else {
      if (request.branchId() != null) {
        if (!branchProvider.existsBranch(request.branchId())) {
          throw new ResourceNotFoundException("Branch with id " + request.branchId() + " does not exist.");
        }
        targetBranchId = request.branchId();
      }
    }

    String role = request.role() != null ? request.role().name() : user.role();
    Boolean isActive = request.isActive() != null ? request.isActive() : user.isActive();

    AppUser updated =
        new AppUser(
            user.id(),
            user.email(),
            request.firstName().trim(),
            request.lastName().trim(),
            request.phoneNumber() != null ? request.phoneNumber().trim() : null,
            role,
            targetBranchId,
            user.createdBy(),
            isActive,
            user.createdAt(),
            OffsetDateTime.now(),
            user.documents(),
            user.addresses());

    userRepository.save(updated);
  }

  @Transactional
  public void deactivateUser(
      UUID callerId,
      boolean isCallerEmpresa,
      Long tokenBranchId,
      UUID targetUserId) {

    if (callerId.equals(targetUserId)) {
      throw new IllegalStateException("Users cannot deactivate their own account.");
    }

    AppUser user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(
                () -> new ResourceNotFoundException("User with id " + targetUserId + " not found."));

    if (!isCallerEmpresa) {
      Long callerBranchId =
          userRepository
              .findById(callerId)
              .map(AppUser::branchId)
              .orElse(tokenBranchId);

      boolean isSameBranch = callerBranchId != null && callerBranchId.equals(user.branchId());
      if (!isSameBranch) {
        throw new AccessDeniedException("You do not have permission to modify this user.");
      }
    }

    AppUser updated =
        new AppUser(
            user.id(),
            user.email(),
            user.firstName(),
            user.lastName(),
            user.phoneNumber(),
            user.role(),
            user.branchId(),
            user.createdBy(),
            false,
            user.createdAt(),
            OffsetDateTime.now(),
            user.documents(),
            user.addresses());

    userRepository.save(updated);
  }

  private UserResponse toResponse(AppUser user) {
    return new UserResponse(
        user.id(),
        user.email(),
        user.firstName(),
        user.lastName(),
        user.phoneNumber(),
        user.role(),
        user.branchId(),
        user.createdBy(),
        user.isActive(),
        user.createdAt());
  }
}
