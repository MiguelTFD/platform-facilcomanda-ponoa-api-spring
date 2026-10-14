package com.facilcomanda.ponoa.table;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import com.facilcomanda.ponoa.table.payload.CreateFloorRequest;
import com.facilcomanda.ponoa.table.payload.CreateTableRequest;
import com.facilcomanda.ponoa.table.payload.FloorResponse;
import com.facilcomanda.ponoa.table.payload.TableResponse;
import com.facilcomanda.ponoa.table.payload.UpdateTableStateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
class TableController {

  private final TableService tableService;
  private final BranchAccessValidator branchAccessValidator;

  TableController(TableService tableService, BranchAccessValidator branchAccessValidator) {
    this.tableService = tableService;
    this.branchAccessValidator = branchAccessValidator;
  }

  @PostMapping("/floors")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> createFloor(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateFloorRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    Long floorId = tableService.createFloor(branchId, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(floorId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @GetMapping("/floors")
  public ResponseEntity<List<FloorResponse>> listFloors(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    List<FloorResponse> floors =
        tableService.getFloors(branchId).stream()
            .map(f -> new FloorResponse(f.id(), f.branchId(), f.name(), f.description()))
            .toList();

    return ResponseEntity.ok(floors);
  }

  @org.springframework.web.bind.annotation.PutMapping("/floors/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateFloor(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody com.facilcomanda.ponoa.table.payload.UpdateFloorRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    tableService.updateFloor(id, branchId, request);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/tables")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> createTable(
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateTableRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    Long tableId = tableService.createTable(branchId, request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(tableId)
            .toUri();

    return ResponseEntity.created(location).build();
  }

  @GetMapping("/tables")
  public ResponseEntity<List<TableResponse>> listTables(
      @RequestParam(required = false) Long floorId,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    List<TableResponse> tables =
        tableService.getTables(branchId, floorId).stream()
            .map(
                t ->
                    new TableResponse(
                        t.id(),
                        t.branchId(),
                        t.floorId(),
                        t.name(),
                        t.description(),
                        t.state(),
                        t.chairs()))
            .toList();

    return ResponseEntity.ok(tables);
  }

  @GetMapping("/tables/{id}")
  public ResponseEntity<TableResponse> getTable(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    RestaurantTable t = tableService.getTable(branchId, id);

    return ResponseEntity.ok(
        new TableResponse(
            t.id(),
            t.branchId(),
            t.floorId(),
            t.name(),
            t.description(),
            t.state(),
            t.chairs()));
  }

  @org.springframework.web.bind.annotation.PutMapping("/tables/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateTable(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody com.facilcomanda.ponoa.table.payload.UpdateTableRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    tableService.updateTable(id, branchId, request);

    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/tables/{id}/state")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> updateTableState(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateTableStateRequest request) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    tableService.updateTableState(id, branchId, request.state());

    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/tables/{id}/deactivate")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deactivateTable(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    tableService.deactivateTable(id, branchId);

    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/tables/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deleteTable(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    tableService.deactivateTable(id, branchId);

    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/floors/{id}/deactivate")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deactivateFloor(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    tableService.deactivateFloor(id, branchId);

    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/floors/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR', 'EMPRESA')")
  public ResponseEntity<Void> deleteFloor(
      @PathVariable Long id,
      @RequestHeader(value = "X-Branch-Id", required = false) Long branchIdHeader,
      @AuthenticationPrincipal Jwt jwt) {

    Long branchId = branchAccessValidator.resolveAndValidateBranchId(jwt, branchIdHeader);
    tableService.deactivateFloor(id, branchId);

    return ResponseEntity.noContent().build();
  }
}

