package com.facilcomanda.ponoa.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.branch.payload.BranchResponse;
import com.facilcomanda.ponoa.branch.payload.CreateBranchRequest;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

  @Mock private BranchRepository branchRepository;

  @InjectMocks private BranchService branchService;

  @Test
  @DisplayName("Should create branch successfully when code and name are unique")
  void shouldCreateBranchSuccessfully() {
    CreateBranchRequest request =
        new CreateBranchRequest(
            "Sucursal Miraflores", "MIF-01", "Av. Larco 123, Miraflores", "999888777");

    when(branchRepository.existsByCode("MIF-01")).thenReturn(false);
    when(branchRepository.existsByName("Sucursal Miraflores")).thenReturn(false);

    Branch savedBranch =
        new Branch(
            1L,
            "Sucursal Miraflores",
            "MIF-01",
            "Av. Larco 123, Miraflores",
            "999888777",
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(branchRepository.save(any(Branch.class))).thenReturn(savedBranch);

    Long branchId = branchService.createBranch(request);

    assertThat(branchId).isEqualTo(1L);
    verify(branchRepository).save(any(Branch.class));
  }

  @Test
  @DisplayName("Should throw DuplicateKeyException when branch code already exists")
  void shouldThrowWhenCodeExists() {
    CreateBranchRequest request =
        new CreateBranchRequest(
            "Sucursal Miraflores", "MIF-01", "Av. Larco 123, Miraflores", "999888777");

    when(branchRepository.existsByCode("MIF-01")).thenReturn(true);

    assertThatThrownBy(() -> branchService.createBranch(request))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("MIF-01");
  }

  @Test
  @DisplayName("Should return branch response when id exists")
  void shouldReturnBranchWhenIdExists() {
    Branch branch =
        new Branch(
            1L,
            "Sucursal Miraflores",
            "MIF-01",
            "Av. Larco 123, Miraflores",
            "999888777",
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));

    BranchResponse response = branchService.getBranchById(1L);

    assertThat(response.id()).isEqualTo(1L);
    assertThat(response.code()).isEqualTo("MIF-01");
    assertThat(response.name()).isEqualTo("Sucursal Miraflores");
    assertThat(response.isActive()).isTrue();
  }

  @Test
  @DisplayName("Should throw ResourceNotFoundException when branch id does not exist")
  void shouldThrowWhenIdNotFound() {
    when(branchRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> branchService.getBranchById(99L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  @Test
  @DisplayName("Should return active branches list")
  void shouldReturnActiveBranches() {
    Branch b1 =
        new Branch(
            1L,
            "Sucursal Miraflores",
            "MIF-01",
            "Av. Larco 123",
            "999888777",
            true,
            null,
            null);
    Branch b2 =
        new Branch(
            2L,
            "Sucursal San Isidro",
            "SI-01",
            "Av. Rivera Navarrete 456",
            "999111222",
            true,
            null,
            null);

    when(branchRepository.findAllByIsActiveTrue()).thenReturn(List.of(b1, b2));

    List<BranchResponse> responses = branchService.getAllActiveBranches();

    assertThat(responses).hasSize(2);
    assertThat(responses.get(0).code()).isEqualTo("MIF-01");
    assertThat(responses.get(1).code()).isEqualTo("SI-01");
  }
}
