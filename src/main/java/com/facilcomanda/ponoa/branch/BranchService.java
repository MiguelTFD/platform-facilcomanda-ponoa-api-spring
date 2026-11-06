package com.facilcomanda.ponoa.branch;

import com.facilcomanda.ponoa.branch.payload.BranchResponse;
import com.facilcomanda.ponoa.branch.payload.CreateBranchRequest;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BranchService implements BranchProvider {

  private final BranchRepository branchRepository;

  BranchService(BranchRepository branchRepository) {
    this.branchRepository = branchRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsBranch(Long branchId) {
    return branchId != null && branchRepository.existsById(branchId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isBranchActive(Long branchId) {
    return branchId != null && branchRepository.findById(branchId).map(Branch::isActive).orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> findBranchName(Long branchId) {
    return branchId != null ? branchRepository.findById(branchId).map(Branch::name) : Optional.empty();
  }

  @Transactional
  public Long createBranch(CreateBranchRequest request) {
    if (branchRepository.existsByCode(request.code())) {
      throw new DuplicateKeyException("Branch with code " + request.code() + " already exists.");
    }
    if (branchRepository.existsByName(request.name())) {
      throw new DuplicateKeyException("Branch with name " + request.name() + " already exists.");
    }

    Branch branch =
        new Branch(
            null,
            request.name(),
            request.code().toUpperCase(),
            request.address(),
            request.phoneNumber(),
            true,
            null,
            null);

    return branchRepository.save(branch).id();
  }

  @Transactional
  public void updateBranch(Long id, com.facilcomanda.ponoa.branch.payload.UpdateBranchRequest request) {
    Branch branch =
        branchRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Branch with id " + id + " not found."));

    if (!branch.name().equalsIgnoreCase(request.name().trim())
        && branchRepository.existsByName(request.name().trim())) {
      throw new DuplicateKeyException("Branch with name " + request.name() + " already exists.");
    }

    branchRepository.updateBranchDetails(
        id,
        request.name().trim(),
        request.address() != null ? request.address().trim() : null,
        request.phoneNumber() != null ? request.phoneNumber().trim() : null,
        request.isActive() != null ? request.isActive() : branch.isActive());
  }

  @Transactional(readOnly = true)
  public BranchResponse getBranchById(Long id) {
    return branchRepository
        .findById(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Branch with id " + id + " not found."));
  }

  @Transactional(readOnly = true)
  public List<BranchResponse> getAllActiveBranches() {
    return branchRepository.findAllByIsActiveTrue().stream().map(this::toResponse).toList();
  }

  private BranchResponse toResponse(Branch branch) {
    return new BranchResponse(
        branch.id(),
        branch.name(),
        branch.code(),
        branch.address(),
        branch.phoneNumber(),
        branch.isActive());
  }
}
