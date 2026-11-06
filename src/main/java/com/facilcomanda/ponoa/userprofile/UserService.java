package com.facilcomanda.ponoa.userprofile;

import com.facilcomanda.ponoa.branch.BranchProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.global.security.UserBranchResolver;
import com.facilcomanda.ponoa.userprofile.payload.CreateAdminUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.CreateCompanyUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.CreateStaffUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.UserResponse;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facilcomanda.ponoa.global.security.KeycloakAdminClient;
import org.springframework.beans.factory.annotation.Autowired;

@Service
class UserService implements UserBranchResolver {

  private final AppUserRepository userRepository;
  private final BranchProvider branchProvider;
  private final KeycloakAdminClient keycloakAdminClient;

  @Autowired
  UserService(
      AppUserRepository userRepository,
      BranchProvider branchProvider,
      Optional<KeycloakAdminClient> keycloakAdminClient) {
    this.userRepository = userRepository;
    this.branchProvider = branchProvider;
    this.keycloakAdminClient = keycloakAdminClient.orElse(null);
  }

  UserService(AppUserRepository userRepository, BranchProvider branchProvider) {
    this(userRepository, branchProvider, Optional.empty());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Long> findBranchIdForUser(UUID userId) {
    return userRepository.findById(userId).map(AppUser::branchId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> findBranchIdsForUser(UUID userId) {
    Set<Long> branches = new LinkedHashSet<>();
    List<Long> assigned = userRepository.findBranchIdsByUserId(userId);
    if (assigned != null) {
      branches.addAll(assigned);
    }
    userRepository.findById(userId).map(AppUser::branchId).ifPresent(branches::add);
    return List.copyOf(branches);
  }

  @Transactional
  public UUID createCompanyUser(CreateCompanyUserRequest request) {
    if (request.email() != null && userRepository.existsByEmail(request.email())) {
      throw new DuplicateKeyException("User with email " + request.email() + " already exists.");
    }

    boolean provisionInKeycloak =
        keycloakAdminClient != null
            && request.password() != null
            && !request.password().isBlank();

    UUID userId;
    boolean keycloakCreated = false;

    if (provisionInKeycloak) {
      userId =
          keycloakAdminClient.createUserWithRole(
              request.email(),
              request.password(),
              request.firstName(),
              request.lastName(),
              AppUserRole.EMPRESA.name());
      keycloakCreated = true;
    } else {
      userId = request.id() != null ? request.id() : UUID.randomUUID();
    }

    OffsetDateTime now = OffsetDateTime.now();

    try {
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
      userRepository.insertUserRole(userId, AppUserRole.EMPRESA.name());
      return userId;
    } catch (Exception ex) {
      if (keycloakCreated) {
        keycloakAdminClient.deleteUser(userId);
      }
      throw ex;
    }
  }

  @Transactional
  public UUID createAdminUser(UUID callerId, CreateAdminUserRequest request) {
    Set<Long> resolvedBranches = new LinkedHashSet<>();
    if (request.branchId() != null) {
      resolvedBranches.add(request.branchId());
    }
    if (request.branchIds() != null) {
      resolvedBranches.addAll(request.branchIds());
    }
    if (resolvedBranches.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one branchId must be provided when creating an administrator.");
    }

    for (Long branchId : resolvedBranches) {
      if (!branchProvider.existsBranch(branchId)) {
        throw new ResourceNotFoundException("Branch with id " + branchId + " does not exist.");
      }
    }

    Set<AppUserRole> resolvedRoles = new LinkedHashSet<>();
    resolvedRoles.add(AppUserRole.ADMINISTRADOR);
    if (request.roles() != null) {
      for (AppUserRole r : request.roles()) {
        if (r == AppUserRole.EMPRESA) {
          throw new IllegalArgumentException("Cannot assign EMPRESA role through admin user creation.");
        }
        resolvedRoles.add(r);
      }
    }

    if (request.email() != null && userRepository.existsByEmail(request.email())) {
      throw new DuplicateKeyException("User with email " + request.email() + " already exists.");
    }

    Long primaryBranchId = resolvedBranches.iterator().next();

    boolean provisionInKeycloak =
        keycloakAdminClient != null
            && request.password() != null
            && !request.password().isBlank();

    UUID userId;
    boolean keycloakCreated = false;

    if (provisionInKeycloak) {
      userId =
          keycloakAdminClient.createUserWithRole(
              request.email(),
              request.password(),
              request.firstName(),
              request.lastName(),
              AppUserRole.ADMINISTRADOR.name(),
              primaryBranchId);
      keycloakCreated = true;

      if (resolvedBranches.size() > 1) {
        keycloakAdminClient.updateUserBranches(
            userId, request.email(), request.firstName(), request.lastName(), resolvedBranches);
      }
      if (resolvedRoles.size() > 1) {
        Set<String> extraRoles =
            resolvedRoles.stream()
                .map(AppUserRole::name)
                .filter(r -> !AppUserRole.ADMINISTRADOR.name().equals(r))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        keycloakAdminClient.assignRealmRoles(userId, extraRoles);
      }
    } else {
      userId = request.id() != null ? request.id() : UUID.randomUUID();
    }

    OffsetDateTime now = OffsetDateTime.now();

    try {
      userRepository.insertDetailedUser(
          userId,
          request.email(),
          request.firstName(),
          request.lastName(),
          request.phoneNumber(),
          AppUserRole.ADMINISTRADOR.name(),
          primaryBranchId,
          callerId,
          true,
          now,
          now);
      for (Long bId : resolvedBranches) {
        userRepository.insertUserBranch(userId, bId);
      }
      for (AppUserRole r : resolvedRoles) {
        userRepository.insertUserRole(userId, r.name());
      }
      return userId;
    } catch (Exception ex) {
      if (keycloakCreated) {
        keycloakAdminClient.deleteUser(userId);
      }
      throw ex;
    }
  }

  @Transactional
  public UUID createStaffUser(
      UUID callerId,
      boolean isCallerEmpresa,
      Long tokenBranchId,
      CreateStaffUserRequest request) {

    Set<AppUserRole> resolvedRoles = new LinkedHashSet<>();
    if (request.role() != null) {
      resolvedRoles.add(request.role());
    }
    if (request.roles() != null) {
      resolvedRoles.addAll(request.roles());
    }
    if (resolvedRoles.isEmpty()) {
      throw new IllegalArgumentException("At least one staff role must be provided.");
    }

    for (AppUserRole role : resolvedRoles) {
      if (role != AppUserRole.COCINERO
          && role != AppUserRole.CAJERO
          && role != AppUserRole.MESERO) {
        throw new IllegalArgumentException(
            "Staff creation only permits roles: COCINERO, CAJERO, MESERO. Provided: " + role);
      }
    }

    AppUserRole primaryRole = resolvedRoles.iterator().next();
    Set<Long> resolvedBranches = new LinkedHashSet<>();

    if (isCallerEmpresa) {
      if (request.branchId() != null) {
        resolvedBranches.add(request.branchId());
      }
      if (request.branchIds() != null) {
        resolvedBranches.addAll(request.branchIds());
      }
      if (resolvedBranches.isEmpty()) {
        throw new IllegalArgumentException(
            "branchId must be provided when company creates a staff member.");
      }
      for (Long bId : resolvedBranches) {
        if (!branchProvider.existsBranch(bId)) {
          throw new ResourceNotFoundException("Branch with id " + bId + " does not exist.");
        }
      }
    } else {
      // Caller is ADMINISTRADOR: resolve allowed branches from DB, fallback to token claim
      Set<Long> adminBranches = resolveCallerBranchIds(callerId, tokenBranchId);
      if (adminBranches.isEmpty()) {
        throw new AccessDeniedException("Administrator is not assigned to any branch.");
      }

      Set<Long> requested = new LinkedHashSet<>();
      if (request.branchId() != null) {
        requested.add(request.branchId());
      }
      if (request.branchIds() != null) {
        requested.addAll(request.branchIds());
      }

      if (requested.isEmpty()) {
        resolvedBranches.add(adminBranches.iterator().next());
      } else {
        for (Long reqBranchId : requested) {
          if (!adminBranches.contains(reqBranchId)) {
            throw new AccessDeniedException(
                "Administrator is not authorized to assign staff to branch " + reqBranchId);
          }
          resolvedBranches.add(reqBranchId);
        }
      }
    }

    Long targetBranchId = resolvedBranches.iterator().next();

    if (request.email() != null && userRepository.existsByEmail(request.email())) {
      throw new DuplicateKeyException("User with email " + request.email() + " already exists.");
    }

    boolean provisionInKeycloak =
        keycloakAdminClient != null
            && request.password() != null
            && !request.password().isBlank();

    UUID userId;
    boolean keycloakCreated = false;

    if (provisionInKeycloak) {
      userId =
          keycloakAdminClient.createUserWithRole(
              request.email(),
              request.password(),
              request.firstName(),
              request.lastName(),
              primaryRole.name(),
              targetBranchId);
      keycloakCreated = true;

      if (resolvedBranches.size() > 1) {
        keycloakAdminClient.updateUserBranches(
            userId, request.email(), request.firstName(), request.lastName(), resolvedBranches);
      }
      if (resolvedRoles.size() > 1) {
        Set<String> extraRoles =
            resolvedRoles.stream()
                .map(AppUserRole::name)
                .filter(r -> !primaryRole.name().equals(r))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        keycloakAdminClient.assignRealmRoles(userId, extraRoles);
      }
    } else {
      userId = request.id() != null ? request.id() : UUID.randomUUID();
    }

    OffsetDateTime now = OffsetDateTime.now();

    try {
      userRepository.insertDetailedUser(
          userId,
          request.email(),
          request.firstName(),
          request.lastName(),
          request.phoneNumber(),
          primaryRole.name(),
          targetBranchId,
          callerId,
          true,
          now,
          now);
      for (Long bId : resolvedBranches) {
        userRepository.insertUserBranch(userId, bId);
      }
      for (AppUserRole r : resolvedRoles) {
        userRepository.insertUserRole(userId, r.name());
      }
      return userId;
    } catch (Exception ex) {
      if (keycloakCreated) {
        keycloakAdminClient.deleteUser(userId);
      }
      throw ex;
    }
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

    Set<Long> callerBranches = resolveCallerBranchIds(callerId, tokenBranchId);
    if (callerBranches.isEmpty()) {
      throw new AccessDeniedException("User is not assigned to any branch.");
    }

    if (filterBranchId != null) {
      if (!callerBranches.contains(filterBranchId)) {
        throw new AccessDeniedException("User is not authorized for branch: " + filterBranchId);
      }
      return userRepository.findAllByBranchId(filterBranchId).stream()
          .map(this::toResponse)
          .toList();
    }

    Long primaryCallerBranch = callerBranches.iterator().next();
    return userRepository.findAllByBranchId(primaryCallerBranch).stream()
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
      boolean isSelf = callerId.equals(targetUserId);
      if (!isSelf && !sharesAnyBranch(callerId, tokenBranchId, user)) {
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

    Set<Long> newBranches = new LinkedHashSet<>();
    if (request.branchId() != null) {
      newBranches.add(request.branchId());
    }
    if (request.branchIds() != null) {
      newBranches.addAll(request.branchIds());
    }

    Set<AppUserRole> newRoles = new LinkedHashSet<>();
    if (request.role() != null) {
      newRoles.add(request.role());
    }
    if (request.roles() != null) {
      newRoles.addAll(request.roles());
    }

    Long targetBranchId = user.branchId();

    if (!isCallerEmpresa) {
      Set<Long> callerBranches = resolveCallerBranchIds(callerId, tokenBranchId);
      if (!sharesAnyBranch(callerBranches, user)) {
        throw new AccessDeniedException("You do not have permission to modify this user.");
      }

      if (newRoles.contains(AppUserRole.EMPRESA)) {
        throw new AccessDeniedException("Administrators cannot promote users to EMPRESA.");
      }

      for (Long bId : newBranches) {
        if (!callerBranches.contains(bId)) {
          throw new AccessDeniedException("Administrators cannot reassign users to other branches.");
        }
      }
      if (!newBranches.isEmpty()) {
        targetBranchId = newBranches.iterator().next();
      }
    } else {
      for (Long bId : newBranches) {
        if (!branchProvider.existsBranch(bId)) {
          throw new ResourceNotFoundException("Branch with id " + bId + " does not exist.");
        }
      }
      if (!newBranches.isEmpty()) {
        targetBranchId = newBranches.iterator().next();
      }
    }

    String role =
        !newRoles.isEmpty() ? newRoles.iterator().next().name() : user.role();
    Boolean isActive = request.isActive() != null ? request.isActive() : user.isActive();

    int updatedRows =
        userRepository.updateUserDetails(
            targetUserId,
            request.firstName().trim(),
            request.lastName().trim(),
            request.phoneNumber() != null ? request.phoneNumber().trim() : null,
            role,
            targetBranchId,
            isActive);

    if (updatedRows == 0) {
      throw new ResourceNotFoundException("User with id " + targetUserId + " not found.");
    }

    if (!newBranches.isEmpty()) {
      userRepository.deleteUserBranches(targetUserId);
      for (Long bId : newBranches) {
        userRepository.insertUserBranch(targetUserId, bId);
      }
    }

    if (!newRoles.isEmpty()) {
      userRepository.deleteUserRoles(targetUserId);
      for (AppUserRole r : newRoles) {
        userRepository.insertUserRole(targetUserId, r.name());
      }
    }

    if (keycloakAdminClient != null) {
      if (request.password() != null && !request.password().isBlank()) {
        keycloakAdminClient.resetUserPassword(targetUserId, request.password());
      }
      if (newBranches.size() > 1) {
        keycloakAdminClient.updateUserBranches(
            targetUserId,
            user.email(),
            request.firstName().trim(),
            request.lastName().trim(),
            newBranches);
      } else if (!java.util.Objects.equals(targetBranchId, user.branchId())) {
        keycloakAdminClient.updateUserBranch(
            targetUserId,
            user.email(),
            request.firstName().trim(),
            request.lastName().trim(),
            targetBranchId);
      }
      if (!newRoles.isEmpty()) {
        keycloakAdminClient.assignRealmRoles(
            targetUserId, newRoles.stream().map(AppUserRole::name).toList());
      }
    }
  }

  @Transactional
  public void assignUserBranches(
      UUID callerId,
      boolean isCallerEmpresa,
      Long tokenBranchId,
      UUID targetUserId,
      Set<Long> branchIds) {
    if (branchIds == null || branchIds.isEmpty()) {
      throw new IllegalArgumentException("At least one branchId must be provided.");
    }

    AppUser user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(
                () -> new ResourceNotFoundException("User with id " + targetUserId + " not found."));

    Set<Long> orderedBranches = new LinkedHashSet<>(branchIds);

    if (!isCallerEmpresa) {
      Set<Long> callerBranches = resolveCallerBranchIds(callerId, tokenBranchId);
      if (!sharesAnyBranch(callerBranches, user)) {
        throw new AccessDeniedException("You do not have permission to modify this user.");
      }
      for (Long bId : orderedBranches) {
        if (!callerBranches.contains(bId)) {
          throw new AccessDeniedException(
              "Administrators cannot assign users to branches outside their own scope: " + bId);
        }
      }
    } else {
      for (Long bId : orderedBranches) {
        if (!branchProvider.existsBranch(bId)) {
          throw new ResourceNotFoundException("Branch with id " + bId + " does not exist.");
        }
      }
    }

    Long primaryBranchId = orderedBranches.iterator().next();

    userRepository.updateUserDetails(
        targetUserId,
        user.firstName(),
        user.lastName(),
        user.phoneNumber(),
        user.role(),
        primaryBranchId,
        user.isActive());

    userRepository.deleteUserBranches(targetUserId);
    for (Long bId : orderedBranches) {
      userRepository.insertUserBranch(targetUserId, bId);
    }

    if (keycloakAdminClient != null) {
      keycloakAdminClient.updateUserBranches(
          targetUserId, user.email(), user.firstName(), user.lastName(), orderedBranches);
    }
  }

  @Transactional
  public void assignUserRoles(
      UUID callerId,
      boolean isCallerEmpresa,
      Long tokenBranchId,
      UUID targetUserId,
      Set<AppUserRole> roles) {
    if (roles == null || roles.isEmpty()) {
      throw new IllegalArgumentException("At least one role must be provided.");
    }

    AppUser user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(
                () -> new ResourceNotFoundException("User with id " + targetUserId + " not found."));

    Set<AppUserRole> orderedRoles = new LinkedHashSet<>(roles);

    if (!isCallerEmpresa) {
      Set<Long> callerBranches = resolveCallerBranchIds(callerId, tokenBranchId);
      if (!sharesAnyBranch(callerBranches, user)) {
        throw new AccessDeniedException("You do not have permission to modify this user.");
      }
      if (orderedRoles.contains(AppUserRole.EMPRESA)) {
        throw new AccessDeniedException("Administrators cannot promote users to EMPRESA.");
      }
    }

    String primaryRole = orderedRoles.iterator().next().name();

    userRepository.updateUserDetails(
        targetUserId,
        user.firstName(),
        user.lastName(),
        user.phoneNumber(),
        primaryRole,
        user.branchId(),
        user.isActive());

    userRepository.deleteUserRoles(targetUserId);
    for (AppUserRole r : orderedRoles) {
      userRepository.insertUserRole(targetUserId, r.name());
    }

    if (keycloakAdminClient != null) {
      keycloakAdminClient.assignRealmRoles(
          targetUserId, orderedRoles.stream().map(AppUserRole::name).toList());
    }
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
      if (!sharesAnyBranch(callerId, tokenBranchId, user)) {
        throw new AccessDeniedException("You do not have permission to modify this user.");
      }
    }

    int updatedRows = userRepository.deactivateUser(targetUserId);
    if (updatedRows == 0) {
      throw new ResourceNotFoundException("User with id " + targetUserId + " not found or already inactive.");
    }
  }

  private Set<Long> resolveCallerBranchIds(UUID callerId, Long tokenBranchId) {
    Set<Long> branches = new LinkedHashSet<>();
    userRepository
        .findById(callerId)
        .map(AppUser::branchId)
        .ifPresent(branches::add);
    List<Long> dbBranches = userRepository.findBranchIdsByUserId(callerId);
    if (dbBranches != null) {
      branches.addAll(dbBranches);
    }
    if (branches.isEmpty() && tokenBranchId != null) {
      branches.add(tokenBranchId);
    }
    return branches;
  }

  private Set<Long> resolveUserBranchIds(AppUser user) {
    Set<Long> branches = new LinkedHashSet<>();
    if (user.branchId() != null) {
      branches.add(user.branchId());
    }
    if (user.id() != null) {
      List<Long> dbBranches = userRepository.findBranchIdsByUserId(user.id());
      if (dbBranches != null) {
        branches.addAll(dbBranches);
      }
    }
    return branches;
  }

  private boolean sharesAnyBranch(UUID callerId, Long tokenBranchId, AppUser targetUser) {
    Set<Long> callerBranches = resolveCallerBranchIds(callerId, tokenBranchId);
    return sharesAnyBranch(callerBranches, targetUser);
  }

  private boolean sharesAnyBranch(Set<Long> callerBranches, AppUser targetUser) {
    if (callerBranches == null || callerBranches.isEmpty()) {
      return false;
    }
    Set<Long> targetBranches = resolveUserBranchIds(targetUser);
    for (Long bId : targetBranches) {
      if (callerBranches.contains(bId)) {
        return true;
      }
    }
    return false;
  }

  private UserResponse toResponse(AppUser user) {
    Set<String> roles = new LinkedHashSet<>();
    if (user.role() != null && !user.role().isBlank()) {
      roles.add(user.role());
    }
    if (user.id() != null) {
      List<String> dbRoles = userRepository.findRolesByUserId(user.id());
      if (dbRoles != null) {
        roles.addAll(dbRoles);
      }
    }

    Set<Long> branchIds = resolveUserBranchIds(user);

    return new UserResponse(
        user.id(),
        user.email(),
        user.firstName(),
        user.lastName(),
        user.phoneNumber(),
        user.role(),
        Set.copyOf(roles),
        user.branchId(),
        Set.copyOf(branchIds),
        user.createdBy(),
        user.isActive(),
        user.createdAt());
  }
}
