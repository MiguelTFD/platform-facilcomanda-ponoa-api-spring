package com.facilcomanda.ponoa.branch;

import com.facilcomanda.ponoa.branch.payload.BranchResponse;
import com.facilcomanda.ponoa.branch.payload.CreateBranchRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/branches")
class BranchController {

  private final BranchService branchService;

  BranchController(BranchService branchService) {
    this.branchService = branchService;
  }

  @PostMapping
  @PreAuthorize("hasRole('EMPRESA')")
  public ResponseEntity<Void> createBranch(@Valid @RequestBody CreateBranchRequest request) {
    Long branchId = branchService.createBranch(request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(branchId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @GetMapping("/{id}")
  public ResponseEntity<BranchResponse> getBranch(@PathVariable Long id) {
    return ResponseEntity.ok(branchService.getBranchById(id));
  }

  @GetMapping
  public ResponseEntity<List<BranchResponse>> listActiveBranches() {
    return ResponseEntity.ok(branchService.getAllActiveBranches());
  }
}
