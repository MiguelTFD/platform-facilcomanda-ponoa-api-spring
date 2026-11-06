package com.facilcomanda.ponoa.table;

import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.table.payload.CreateFloorRequest;
import com.facilcomanda.ponoa.table.payload.CreateTableRequest;
import com.facilcomanda.ponoa.table.payload.CreateZoneRequest;
import com.facilcomanda.ponoa.table.payload.UpdateFloorRequest;
import com.facilcomanda.ponoa.table.payload.UpdateTableRequest;
import com.facilcomanda.ponoa.table.payload.UpdateZoneRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TableService implements RestaurantTableProvider {

  private final FloorRepository floorRepository;
  private final ZoneRepository zoneRepository;
  private final TableRepository tableRepository;
  private final TableEventPublisher tableEventPublisher;

  @Autowired
  TableService(
      FloorRepository floorRepository,
      ZoneRepository zoneRepository,
      TableRepository tableRepository,
      TableEventPublisher tableEventPublisher) {
    this.floorRepository = floorRepository;
    this.zoneRepository = zoneRepository;
    this.tableRepository = tableRepository;
    this.tableEventPublisher = tableEventPublisher;
  }

  TableService(
      FloorRepository floorRepository,
      TableRepository tableRepository,
      TableEventPublisher tableEventPublisher) {
    this(floorRepository, null, tableRepository, tableEventPublisher);
  }

  @Transactional
  public Long createFloor(Long branchId, CreateFloorRequest request) {
    if (floorRepository.existsByBranchIdAndName(branchId, request.name())) {
      throw new DuplicateKeyException(
          "Floor with name '" + request.name() + "' already exists in branch " + branchId);
    }

    RestaurantFloor floor =
        new RestaurantFloor(null, branchId, request.name(), request.description(), null, null);
    return floorRepository.save(floor).id();
  }

  @Transactional
  public void updateFloor(Long floorId, Long branchId, UpdateFloorRequest request) {
    RestaurantFloor existing =
        floorRepository
            .findByIdAndBranchId(floorId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Floor " + floorId + " not found in branch " + branchId));

    if (!existing.name().equalsIgnoreCase(request.name().trim())
        && floorRepository.existsByBranchIdAndName(branchId, request.name().trim())) {
      throw new DuplicateKeyException(
          "Floor with name '" + request.name() + "' already exists in branch " + branchId);
    }

    floorRepository.updateFloor(
        floorId,
        branchId,
        request.name().trim(),
        request.description() != null ? request.description().trim() : null);
  }

  @Transactional(readOnly = true)
  public List<RestaurantFloor> getFloors(Long branchId) {
    return floorRepository.findAllByBranchId(branchId);
  }

  @Transactional
  public void deactivateFloor(Long floorId, Long branchId) {
    floorRepository
        .findByIdAndBranchId(floorId, branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Floor " + floorId + " not found in branch " + branchId));

    List<RestaurantTable> tables = tableRepository.findAllByBranchIdAndFloorId(branchId, floorId);
    boolean hasOccupiedTables = tables.stream().anyMatch(t -> t.state() == TableState.OCCUPIED);
    if (hasOccupiedTables) {
      throw new IllegalStateException(
          "Cannot deactivate floor " + floorId + " because one or more tables are currently occupied");
    }

    tableRepository.softDeleteTablesByFloor(floorId, branchId);
    if (zoneRepository != null) {
      zoneRepository.softDeleteZonesByFloor(floorId, branchId);
    }
    int updated = floorRepository.softDeleteFloor(floorId, branchId);
    if (updated == 0) {
      throw new ResourceNotFoundException("Floor " + floorId + " not found in branch " + branchId);
    }
  }

  @Transactional
  public Long createZone(Long branchId, CreateZoneRequest request) {
    floorRepository
        .findByIdAndBranchId(request.floorId(), branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Floor " + request.floorId() + " not found in branch " + branchId));

    String trimmedName = request.name().trim();
    if (zoneRepository.existsByFloorIdAndName(request.floorId(), trimmedName)) {
      throw new DuplicateKeyException(
          "Zone with name '" + trimmedName + "' already exists in floor " + request.floorId());
    }

    RestaurantZone zone =
        new RestaurantZone(
            null,
            branchId,
            request.floorId(),
            trimmedName,
            request.description() != null ? request.description().trim() : null,
            null,
            null);

    return zoneRepository.save(zone).id();
  }

  @Transactional(readOnly = true)
  public List<RestaurantZone> getZones(Long branchId, Long floorId) {
    if (floorId != null) {
      return zoneRepository.findAllByBranchIdAndFloorId(branchId, floorId);
    }
    return zoneRepository.findAllByBranchId(branchId);
  }

  @Transactional(readOnly = true)
  public RestaurantZone getZone(Long branchId, Long zoneId) {
    return zoneRepository
        .findByIdAndBranchId(zoneId, branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Zone " + zoneId + " not found in branch " + branchId));
  }

  @Transactional
  public void updateZone(Long zoneId, Long branchId, UpdateZoneRequest request) {
    RestaurantZone existing = getZone(branchId, zoneId);

    floorRepository
        .findByIdAndBranchId(request.floorId(), branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Floor " + request.floorId() + " not found in branch " + branchId));

    String trimmedName = request.name().trim();
    if ((!existing.floorId().equals(request.floorId())
            || !existing.name().equalsIgnoreCase(trimmedName))
        && zoneRepository.existsByFloorIdAndName(request.floorId(), trimmedName)) {
      throw new DuplicateKeyException(
          "Zone with name '" + trimmedName + "' already exists in floor " + request.floorId());
    }

    int updated =
        zoneRepository.updateZone(
            zoneId,
            branchId,
            request.floorId(),
            trimmedName,
            request.description() != null ? request.description().trim() : null);

    if (updated == 0) {
      throw new ResourceNotFoundException("Zone " + zoneId + " not found in branch " + branchId);
    }

    if (!existing.floorId().equals(request.floorId())) {
      tableRepository.updateFloorByZone(zoneId, branchId, request.floorId());
    }
  }

  @Transactional
  public void deactivateZone(Long zoneId, Long branchId) {
    getZone(branchId, zoneId);

    List<RestaurantTable> tables = tableRepository.findAllByBranchIdAndZoneId(branchId, zoneId);
    boolean hasOccupiedTables = tables.stream().anyMatch(t -> t.state() == TableState.OCCUPIED);
    if (hasOccupiedTables) {
      throw new IllegalStateException(
          "Cannot deactivate zone " + zoneId + " because one or more tables are currently occupied");
    }

    tableRepository.softDeleteTablesByZone(zoneId, branchId);
    int updated = zoneRepository.softDeleteZone(zoneId, branchId);
    if (updated == 0) {
      throw new ResourceNotFoundException("Zone " + zoneId + " not found in branch " + branchId);
    }
  }

  @Transactional
  public Long createTable(Long branchId, CreateTableRequest request) {
    Long resolvedFloorId = request.floorId();

    if (request.zoneId() != null) {
      RestaurantZone zone =
          zoneRepository
              .findByIdAndBranchId(request.zoneId(), branchId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Zone " + request.zoneId() + " not found in branch " + branchId));

      if (resolvedFloorId != null && !zone.floorId().equals(resolvedFloorId)) {
        throw new IllegalArgumentException(
            "Zone " + request.zoneId() + " does not belong to floor " + resolvedFloorId);
      }
      resolvedFloorId = zone.floorId();
    }

    if (resolvedFloorId == null) {
      throw new IllegalArgumentException(
          "Either floorId or zoneId must be provided when creating a table.");
    }

    final Long targetFloorId = resolvedFloorId;
    floorRepository
        .findByIdAndBranchId(targetFloorId, branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Floor " + targetFloorId + " not found in branch " + branchId));

    if (tableRepository.existsByFloorIdAndName(targetFloorId, request.name())) {
      throw new DuplicateKeyException(
          "Table with name '" + request.name() + "' already exists in floor " + targetFloorId);
    }

    RestaurantTable table =
        new RestaurantTable(
            null,
            branchId,
            targetFloorId,
            request.zoneId(),
            request.name(),
            request.description(),
            TableState.AVAILABLE,
            request.chairs() != null ? request.chairs() : 4,
            null,
            null);

    return tableRepository.save(table).id();
  }

  @Transactional(readOnly = true)
  public List<RestaurantTable> getTables(Long branchId, Long floorId) {
    return getTables(branchId, floorId, null);
  }

  @Transactional(readOnly = true)
  public List<RestaurantTable> getTables(Long branchId, Long floorId, Long zoneId) {
    if (floorId != null && zoneId != null) {
      return tableRepository.findAllByBranchIdAndFloorIdAndZoneId(branchId, floorId, zoneId);
    }
    if (zoneId != null) {
      return tableRepository.findAllByBranchIdAndZoneId(branchId, zoneId);
    }
    if (floorId != null) {
      return tableRepository.findAllByBranchIdAndFloorId(branchId, floorId);
    }
    return tableRepository.findAllByBranchId(branchId);
  }

  @Transactional(readOnly = true)
  public RestaurantTable getTable(Long branchId, Long tableId) {
    return tableRepository
        .findByIdAndBranchId(tableId, branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Table " + tableId + " not found in branch " + branchId));
  }

  @Transactional
  public void updateTable(Long tableId, Long branchId, UpdateTableRequest request) {
    RestaurantTable existing = getTable(branchId, tableId);

    Long resolvedFloorId = request.floorId() != null ? request.floorId() : existing.floorId();

    if (request.zoneId() != null) {
      RestaurantZone zone =
          zoneRepository
              .findByIdAndBranchId(request.zoneId(), branchId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Zone " + request.zoneId() + " not found in branch " + branchId));

      if (request.floorId() != null && !zone.floorId().equals(request.floorId())) {
        throw new IllegalArgumentException(
            "Zone " + request.zoneId() + " does not belong to floor " + request.floorId());
      }
      resolvedFloorId = zone.floorId();
    }

    final Long targetFloorId = resolvedFloorId;
    // Verify target floor belongs to this branch
    floorRepository
        .findByIdAndBranchId(targetFloorId, branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Floor " + targetFloorId + " not found in branch " + branchId));

    if ((!existing.floorId().equals(targetFloorId)
            || !existing.name().equalsIgnoreCase(request.name().trim()))
        && tableRepository.existsByFloorIdAndName(targetFloorId, request.name().trim())) {
      throw new DuplicateKeyException(
          "Table with name '" + request.name() + "' already exists in floor " + targetFloorId);
    }

    if (request.zoneId() != null || existing.zoneId() != null) {
      tableRepository.updateTableDetailsWithZone(
          tableId,
          branchId,
          targetFloorId,
          request.zoneId(),
          request.name().trim(),
          request.description() != null ? request.description().trim() : null,
          request.chairs());
    } else {
      tableRepository.updateTableDetails(
          tableId,
          branchId,
          targetFloorId,
          request.name().trim(),
          request.description() != null ? request.description().trim() : null,
          request.chairs());
    }
  }

  @Transactional
  public void deactivateTable(Long tableId, Long branchId) {
    RestaurantTable table = getTable(branchId, tableId);
    if (table.state() == TableState.OCCUPIED) {
      throw new IllegalStateException(
          "Cannot deactivate table " + tableId + " while it is occupied");
    }
    int updated = tableRepository.softDeleteTable(tableId, branchId);
    if (updated == 0) {
      throw new ResourceNotFoundException("Table " + tableId + " not found in branch " + branchId);
    }
  }

  @Override
  @Transactional
  public void updateTableState(Long tableId, Long branchId, TableState newState) {
    int updated = tableRepository.updateTableState(tableId, branchId, newState.name());
    if (updated == 0) {
      throw new ResourceNotFoundException(
          "Table " + tableId + " not found in branch " + branchId);
    }
    tableEventPublisher.publishTableStateChanged(branchId, tableId, newState, null);
  }

  @Override
  @Transactional
  public boolean occupyTableAtomically(Long tableId, Long branchId) {
    int updated = tableRepository.occupyTableAtomically(tableId, branchId);
    if (updated > 0) {
      tableEventPublisher.publishTableStateChanged(branchId, tableId, TableState.OCCUPIED, null);
      return true;
    }
    return false;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RestaurantTableView> findTableByIdAndBranch(Long tableId, Long branchId) {
    return tableRepository.findTableView(tableId, branchId);
  }

  @Override
  @Transactional
  public Optional<RestaurantTableView> lockTableByIdAndBranch(Long tableId, Long branchId) {
    return tableRepository.findTableViewForUpdate(tableId, branchId);
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Map<Long, String> getTableNames(
      java.util.Collection<Long> tableIds, Long branchId) {
    if (tableIds == null || tableIds.isEmpty()) {
      return java.util.Map.of();
    }
    return tableRepository.findTableViews(tableIds, branchId).stream()
        .collect(
            java.util.stream.Collectors.toMap(
                RestaurantTableView::id, RestaurantTableView::tableName, (a, b) -> a));
  }
}
