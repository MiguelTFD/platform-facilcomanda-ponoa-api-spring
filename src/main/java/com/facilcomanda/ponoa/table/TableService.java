package com.facilcomanda.ponoa.table;

import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.table.payload.CreateFloorRequest;
import com.facilcomanda.ponoa.table.payload.CreateTableRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TableService implements RestaurantTableProvider {

  private final FloorRepository floorRepository;
  private final TableRepository tableRepository;

  TableService(FloorRepository floorRepository, TableRepository tableRepository) {
    this.floorRepository = floorRepository;
    this.tableRepository = tableRepository;
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

  @Transactional(readOnly = true)
  public List<RestaurantFloor> getFloors(Long branchId) {
    return floorRepository.findAllByBranchId(branchId);
  }

  @Transactional
  public Long createTable(Long branchId, CreateTableRequest request) {
    floorRepository
        .findByIdAndBranchId(request.floorId(), branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Floor " + request.floorId() + " not found in branch " + branchId));

    if (tableRepository.existsByFloorIdAndName(request.floorId(), request.name())) {
      throw new DuplicateKeyException(
          "Table with name '" + request.name() + "' already exists in floor " + request.floorId());
    }

    RestaurantTable table =
        new RestaurantTable(
            null,
            branchId,
            request.floorId(),
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

  @Override
  @Transactional
  public void updateTableState(Long tableId, Long branchId, TableState newState) {
    int updated = tableRepository.updateTableState(tableId, branchId, newState.name());
    if (updated == 0) {
      throw new ResourceNotFoundException(
          "Table " + tableId + " not found in branch " + branchId);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RestaurantTableView> findTableByIdAndBranch(Long tableId, Long branchId) {
    return tableRepository.findTableView(tableId, branchId);
  }
}
