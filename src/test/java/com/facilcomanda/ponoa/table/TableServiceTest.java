package com.facilcomanda.ponoa.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.table.payload.CreateFloorRequest;
import com.facilcomanda.ponoa.table.payload.CreateTableRequest;
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
class TableServiceTest {

  @Mock private FloorRepository floorRepository;
  @Mock private TableRepository tableRepository;
  @Mock private TableEventPublisher tableEventPublisher;

  @InjectMocks private TableService tableService;

  @Test
  @DisplayName("Should create floor successfully for branch")
  void shouldCreateFloorSuccessfully() {
    CreateFloorRequest request = new CreateFloorRequest("Terraza", "Piso al aire libre");
    when(floorRepository.existsByBranchIdAndName(1L, "Terraza")).thenReturn(false);

    RestaurantFloor floor = new RestaurantFloor(10L, 1L, "Terraza", "Piso al aire libre", null, null);
    when(floorRepository.save(any(RestaurantFloor.class))).thenReturn(floor);

    Long id = tableService.createFloor(1L, request);

    assertThat(id).isEqualTo(10L);
    verify(floorRepository).save(any(RestaurantFloor.class));
  }

  @Test
  @DisplayName("Should throw DuplicateKeyException when floor name already exists in branch")
  void shouldThrowWhenDuplicateFloorName() {
    CreateFloorRequest request = new CreateFloorRequest("Terraza", null);
    when(floorRepository.existsByBranchIdAndName(1L, "Terraza")).thenReturn(true);

    assertThatThrownBy(() -> tableService.createFloor(1L, request))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("Terraza");
  }

  @Test
  @DisplayName("Should create table in floor successfully")
  void shouldCreateTableSuccessfully() {
    CreateTableRequest request = new CreateTableRequest(10L, "Mesa 1", "Ventana", 4);

    RestaurantFloor floor = new RestaurantFloor(10L, 1L, "Terraza", null, null, null);
    when(floorRepository.findByIdAndBranchId(10L, 1L)).thenReturn(Optional.of(floor));
    when(tableRepository.existsByFloorIdAndName(10L, "Mesa 1")).thenReturn(false);

    RestaurantTable table =
        new RestaurantTable(101L, 1L, 10L, "Mesa 1", "Ventana", TableState.AVAILABLE, 4, null, null);
    when(tableRepository.save(any(RestaurantTable.class))).thenReturn(table);

    Long tableId = tableService.createTable(1L, request);

    assertThat(tableId).isEqualTo(101L);
    verify(tableRepository).save(any(RestaurantTable.class));
  }

  @Test
  @DisplayName("Should throw ResourceNotFoundException when floor does not belong to branch")
  void shouldThrowWhenFloorNotFoundInBranch() {
    CreateTableRequest request = new CreateTableRequest(999L, "Mesa 1", null, 4);
    when(floorRepository.findByIdAndBranchId(999L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> tableService.createTable(1L, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Floor 999 not found");
  }

  @Test
  @DisplayName("Should throw DuplicateKeyException when table name already exists in floor")
  void shouldThrowWhenDuplicateTableNameInFloor() {
    CreateTableRequest request = new CreateTableRequest(10L, "Mesa 1", null, 4);

    RestaurantFloor floor = new RestaurantFloor(10L, 1L, "Terraza", null, null, null);
    when(floorRepository.findByIdAndBranchId(10L, 1L)).thenReturn(Optional.of(floor));
    when(tableRepository.existsByFloorIdAndName(10L, "Mesa 1")).thenReturn(true);

    assertThatThrownBy(() -> tableService.createTable(1L, request))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("Mesa 1");
  }

  @Test
  @DisplayName("Should update table state successfully")
  void shouldUpdateTableStateSuccessfully() {
    when(tableRepository.updateTableState(101L, 1L, "OCCUPIED")).thenReturn(1);

    tableService.updateTableState(101L, 1L, TableState.OCCUPIED);

    verify(tableRepository).updateTableState(101L, 1L, "OCCUPIED");
    verify(tableEventPublisher).publishTableStateChanged(1L, 101L, TableState.OCCUPIED, null);
  }

  @Test
  @DisplayName("Should occupy table atomically and publish live event")
  void shouldOccupyTableAtomicallyAndPublishEvent() {
    when(tableRepository.occupyTableAtomically(101L, 1L)).thenReturn(1);

    boolean result = tableService.occupyTableAtomically(101L, 1L);

    assertThat(result).isTrue();
    verify(tableRepository).occupyTableAtomically(101L, 1L);
    verify(tableEventPublisher).publishTableStateChanged(1L, 101L, TableState.OCCUPIED, null);
  }

  @Test
  @DisplayName("Should return false when atomic table occupation fails (already occupied)")
  void shouldReturnFalseWhenAtomicOccupationFails() {
    when(tableRepository.occupyTableAtomically(101L, 1L)).thenReturn(0);

    boolean result = tableService.occupyTableAtomically(101L, 1L);

    assertThat(result).isFalse();
    verify(tableRepository).occupyTableAtomically(101L, 1L);
  }

  @Test
  @DisplayName("Should throw ResourceNotFoundException when table not found to update state")
  void shouldThrowWhenTableNotFoundToUpdateState() {
    when(tableRepository.updateTableState(999L, 1L, "OCCUPIED")).thenReturn(0);

    assertThatThrownBy(() -> tableService.updateTableState(999L, 1L, TableState.OCCUPIED))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Table 999 not found");
  }

  @Test
  @DisplayName("Should deactivate table successfully when AVAILABLE")
  void shouldDeactivateTableSuccessfully() {
    RestaurantTable table =
        new RestaurantTable(101L, 1L, 10L, "Mesa 1", null, TableState.AVAILABLE, 4, null, null);
    when(tableRepository.findByIdAndBranchId(101L, 1L)).thenReturn(Optional.of(table));
    when(tableRepository.softDeleteTable(101L, 1L)).thenReturn(1);

    tableService.deactivateTable(101L, 1L);

    verify(tableRepository).softDeleteTable(101L, 1L);
  }

  @Test
  @DisplayName("Should throw IllegalStateException when trying to deactivate OCCUPIED table")
  void shouldThrowWhenDeactivatingOccupiedTable() {
    RestaurantTable table =
        new RestaurantTable(101L, 1L, 10L, "Mesa 1", null, TableState.OCCUPIED, 4, null, null);
    when(tableRepository.findByIdAndBranchId(101L, 1L)).thenReturn(Optional.of(table));

    assertThatThrownBy(() -> tableService.deactivateTable(101L, 1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("occupied");
  }

  @Test
  @DisplayName("Should deactivate floor and its tables when no table is occupied")
  void shouldDeactivateFloorSuccessfully() {
    RestaurantFloor floor = new RestaurantFloor(10L, 1L, "Terraza", null, null, null);
    when(floorRepository.findByIdAndBranchId(10L, 1L)).thenReturn(Optional.of(floor));

    RestaurantTable table1 =
        new RestaurantTable(101L, 1L, 10L, "M1", null, TableState.AVAILABLE, 4, null, null);
    when(tableRepository.findAllByBranchIdAndFloorId(1L, 10L)).thenReturn(List.of(table1));
    when(floorRepository.softDeleteFloor(10L, 1L)).thenReturn(1);

    tableService.deactivateFloor(10L, 1L);

    verify(tableRepository).softDeleteTablesByFloor(10L, 1L);
    verify(floorRepository).softDeleteFloor(10L, 1L);
  }

  @Test
  @DisplayName("Should throw IllegalStateException when deactivating floor with occupied tables")
  void shouldThrowWhenDeactivatingFloorWithOccupiedTables() {
    RestaurantFloor floor = new RestaurantFloor(10L, 1L, "Terraza", null, null, null);
    when(floorRepository.findByIdAndBranchId(10L, 1L)).thenReturn(Optional.of(floor));

    RestaurantTable tableOccupied =
        new RestaurantTable(101L, 1L, 10L, "M1", null, TableState.OCCUPIED, 4, null, null);
    when(tableRepository.findAllByBranchIdAndFloorId(1L, 10L)).thenReturn(List.of(tableOccupied));

    assertThatThrownBy(() -> tableService.deactivateFloor(10L, 1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("occupied");
  }
}

