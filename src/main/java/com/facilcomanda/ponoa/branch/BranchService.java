package com.facilcomanda.ponoa.branch;

import com.facilcomanda.ponoa.branch.payload.BranchResponse;
import com.facilcomanda.ponoa.branch.payload.CreateBranchRequest;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BranchService {

  private final BranchRepository branchRepository;

  BranchService(BranchRepository branchRepository) {
    this.branchRepository = branchRepository;
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
