package com.facilcomanda.ponoa.userprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.branch.BranchProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.userprofile.payload.CreateAdminUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.CreateCompanyUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.CreateStaffUserRequest;
import com.facilcomanda.ponoa.userprofile.payload.UserResponse;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private AppUserRepository userRepository;
  @Mock private BranchProvider branchProvider;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepository, branchProvider);
  }

  @Test
  @DisplayName("Should create EMPRESA user successfully with null branchId")
  void shouldCreateCompanyUserSuccessfully() {
    CreateCompanyUserRequest request =
        new CreateCompanyUserRequest(null, "empresa@facilcomanda.com", "Acme", "Corp", "999888777");

    when(userRepository.existsByEmail("empresa@facilcomanda.com")).thenReturn(false);

    UUID createdId = userService.createCompanyUser(request);

    assertThat(createdId).isNotNull();
    verify(userRepository)
        .insertDetailedUser(
            eq(createdId),
            eq("empresa@facilcomanda.com"),
            eq("Acme"),
            eq("Corp"),
            eq("999888777"),
            eq("EMPRESA"),
            eq(null),
            eq(null),
            eq(true),
            any(),
            any());
  }

  @Test
  @DisplayName("Should throw DuplicateKeyException when creating EMPRESA with existing email")
  void shouldFailCreatingCompanyUserWithDuplicateEmail() {
    CreateCompanyUserRequest request =
        new CreateCompanyUserRequest(null, "existing@facilcomanda.com", "Acme", "Corp", "999888777");

    when(userRepository.existsByEmail("existing@facilcomanda.com")).thenReturn(true);

    assertThatThrownBy(() -> userService.createCompanyUser(request))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  @DisplayName("Should create ADMINISTRADOR linked to target branch when created by EMPRESA")
  void shouldCreateAdminUserSuccessfully() {
    UUID callerId = UUID.randomUUID();
    CreateAdminUserRequest request =
        new CreateAdminUserRequest(null, "admin.sede1@facilcomanda.com", "Carlos", "Perez", "987654321", 10L);

    when(branchProvider.existsBranch(10L)).thenReturn(true);
    when(userRepository.existsByEmail("admin.sede1@facilcomanda.com")).thenReturn(false);

    UUID createdId = userService.createAdminUser(callerId, request);

    assertThat(createdId).isNotNull();
    verify(userRepository)
        .insertDetailedUser(
            eq(createdId),
            eq("admin.sede1@facilcomanda.com"),
            eq("Carlos"),
            eq("Perez"),
            eq("987654321"),
            eq("ADMINISTRADOR"),
            eq(10L),
            eq(callerId),
            eq(true),
            any(),
            any());
  }

  @Test
  @DisplayName("Should fail creating ADMINISTRADOR if target branch does not exist")
  void shouldFailCreatingAdminWhenBranchNotFound() {
    UUID callerId = UUID.randomUUID();
    CreateAdminUserRequest request =
        new CreateAdminUserRequest(null, "admin@facilcomanda.com", "Carlos", "Perez", "987654321", 999L);

    when(branchProvider.existsBranch(999L)).thenReturn(false);

    assertThatThrownBy(() -> userService.createAdminUser(callerId, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Branch with id 999 does not exist");
  }

  @Test
  @DisplayName("Staff user created by ADMINISTRADOR must automatically inherit administrator's branchId")
  void shouldCreateStaffInheritingAdminBranch() {
    UUID adminCallerId = UUID.randomUUID();
    AppUser adminUser =
        new AppUser(
            adminCallerId,
            "admin@facilcomanda.com",
            "Admin",
            "Principal",
            "999111222",
            "ADMINISTRADOR",
            5L,
            null,
            true,
            OffsetDateTime.now(),
            null,
            Collections.emptySet(),
            Collections.emptySet());

    when(userRepository.findById(adminCallerId)).thenReturn(Optional.of(adminUser));
    when(userRepository.existsByEmail("mesero@facilcomanda.com")).thenReturn(false);

    CreateStaffUserRequest request =
        new CreateStaffUserRequest(
            null, "mesero@facilcomanda.com", "Juan", "Mozo", "911222333", AppUserRole.MESERO, null);

    UUID createdId = userService.createStaffUser(adminCallerId, false, null, request);

    assertThat(createdId).isNotNull();
    verify(userRepository)
        .insertDetailedUser(
            eq(createdId),
            eq("mesero@facilcomanda.com"),
            eq("Juan"),
            eq("Mozo"),
            eq("911222333"),
            eq("MESERO"),
            eq(5L), // Automatically inherited from admin
            eq(adminCallerId),
            eq(true),
            any(),
            any());
  }

  @Test
  @DisplayName("Staff creation must reject roles other than COCINERO, CAJERO, MESERO")
  void shouldRejectInvalidStaffRoles() {
    UUID adminCallerId = UUID.randomUUID();
    CreateStaffUserRequest request =
        new CreateStaffUserRequest(
            null, "fraud@facilcomanda.com", "Mal", "Actor", "900000000", AppUserRole.ADMINISTRADOR, null);

    assertThatThrownBy(() -> userService.createStaffUser(adminCallerId, false, 5L, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Staff creation only permits roles: COCINERO, CAJERO, MESERO");
  }

  @Test
  @DisplayName("Staff creation by ADMINISTRADOR must fail if administrator has no branch assigned")
  void shouldFailCreatingStaffWhenAdminHasNoBranch() {
    UUID adminCallerId = UUID.randomUUID();
    when(userRepository.findById(adminCallerId)).thenReturn(Optional.empty());

    CreateStaffUserRequest request =
        new CreateStaffUserRequest(
            null, "cocinero@facilcomanda.com", "Pedro", "Chef", "955555555", AppUserRole.COCINERO, null);

    assertThatThrownBy(() -> userService.createStaffUser(adminCallerId, false, null, request))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Administrator is not assigned to any branch");
  }

  @Test
  @DisplayName("Staff user created by EMPRESA requires explicit valid branchId")
  void shouldCreateStaffByEmpresaWithExplicitBranch() {
    UUID empresaCallerId = UUID.randomUUID();
    when(branchProvider.existsBranch(3L)).thenReturn(true);
    when(userRepository.existsByEmail("cajero@facilcomanda.com")).thenReturn(false);

    CreateStaffUserRequest request =
        new CreateStaffUserRequest(
            null, "cajero@facilcomanda.com", "Maria", "Caja", "944444444", AppUserRole.CAJERO, 3L);

    UUID createdId = userService.createStaffUser(empresaCallerId, true, null, request);

    assertThat(createdId).isNotNull();
    verify(userRepository)
        .insertDetailedUser(
            eq(createdId),
            eq("cajero@facilcomanda.com"),
            eq("Maria"),
            eq("Caja"),
            eq("944444444"),
            eq("CAJERO"),
            eq(3L),
            eq(empresaCallerId),
            eq(true),
            any(),
            any());
  }

  @Test
  @DisplayName("ADMINISTRADOR listing users only retrieves users belonging to their branch")
  void shouldListUsersScopedToBranchForAdmin() {
    UUID adminCallerId = UUID.randomUUID();
    AppUser admin =
        new AppUser(
            adminCallerId,
            "admin@facilcomanda.com",
            "Admin",
            "Branch",
            "999",
            "ADMINISTRADOR",
            7L,
            null,
            true,
            OffsetDateTime.now(),
            null,
            Collections.emptySet(),
            Collections.emptySet());

    AppUser staff1 =
        new AppUser(
            UUID.randomUUID(),
            "m1@facilcomanda.com",
            "M1",
            "L1",
            "111",
            "MESERO",
            7L,
            adminCallerId,
            true,
            OffsetDateTime.now(),
            null,
            Collections.emptySet(),
            Collections.emptySet());

    when(userRepository.findById(adminCallerId)).thenReturn(Optional.of(admin));
    when(userRepository.findAllByBranchId(7L)).thenReturn(List.of(admin, staff1));

    List<UserResponse> result = userService.listUsers(adminCallerId, false, null, null);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).branchId()).isEqualTo(7L);
    assertThat(result.get(1).branchId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("ADMINISTRADOR viewing user from another branch must be denied")
  void shouldDenyAdminViewingUserFromDifferentBranch() {
    UUID adminCallerId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    AppUser admin =
        new AppUser(
            adminCallerId,
            "admin@facilcomanda.com",
            "Admin",
            "Branch",
            "999",
            "ADMINISTRADOR",
            7L,
            null,
            true,
            OffsetDateTime.now(),
            null,
            Collections.emptySet(),
            Collections.emptySet());

    AppUser targetUser =
        new AppUser(
            targetUserId,
            "other@facilcomanda.com",
            "Other",
            "User",
            "888",
            "MESERO",
            99L, // Different branch
            null,
            true,
            OffsetDateTime.now(),
            null,
            Collections.emptySet(),
            Collections.emptySet());

    when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
    when(userRepository.findById(adminCallerId)).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> userService.getUserById(adminCallerId, false, null, targetUserId))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("permission");
  }

  @Test
  @DisplayName("EMPRESA can view user from any branch")
  void shouldAllowEmpresaToViewAnyUser() {
    UUID empresaCallerId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    AppUser targetUser =
        new AppUser(
            targetUserId,
            "target@facilcomanda.com",
            "Target",
            "User",
            "888",
            "MESERO",
            99L,
            null,
            true,
            OffsetDateTime.now(),
            null,
            Collections.emptySet(),
            Collections.emptySet());

    when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

    UserResponse response = userService.getUserById(empresaCallerId, true, null, targetUserId);

    assertThat(response.id()).isEqualTo(targetUserId);
    assertThat(response.branchId()).isEqualTo(99L);
  }
}
