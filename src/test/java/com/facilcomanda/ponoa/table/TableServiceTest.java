package com.facilcomanda.ponoa.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.table.payload.CreateFloorRequest;
import com.facilcomanda.ponoa.table.payload.CreateTableRequest;
import com.facilcomanda.ponoa.table.payload.CreateZoneRequest;
import com.facilcomanda.ponoa.table.payload.UpdateFloorRequest;
import com.facilcomanda.ponoa.table.payload.UpdateTableRequest;
import com.facilcomanda.ponoa.table.payload.UpdateZoneRequest;
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
  @Mock private ZoneRepository zoneRepository;
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

  @Test
  @DisplayName("Should update floor details with targeted query")
  void shouldUpdateFloorSuccessfully() {
    RestaurantFloor floor = new RestaurantFloor(10L, 1L, "Terraza", "Vieja desc", null, null);
    when(floorRepository.findByIdAndBranchId(10L, 1L)).thenReturn(Optional.of(floor));
    when(floorRepository.existsByBranchIdAndName(1L, "Terraza Vip")).thenReturn(false);
    when(floorRepository.updateFloor(10L, 1L, "Terraza Vip", "Nueva desc")).thenReturn(1);

    UpdateFloorRequest request = new UpdateFloorRequest("Terraza Vip", "Nueva desc");

    tableService.updateFloor(10L, 1L, request);

    verify(floorRepository).updateFloor(10L, 1L, "Terraza Vip", "Nueva desc");
  }

  @Test
  @DisplayName("Should update table details with targeted query")
  void shouldUpdateTableSuccessfully() {
    RestaurantTable table =
        new RestaurantTable(101L, 1L, 10L, "Mesa 1", null, TableState.AVAILABLE, 4, null, null);
    when(tableRepository.findByIdAndBranchId(101L, 1L)).thenReturn(Optional.of(table));
    when(floorRepository.findByIdAndBranchId(10L, 1L))
        .thenReturn(Optional.of(new RestaurantFloor(10L, 1L, "Terraza", null, null, null)));
    when(tableRepository.updateTableDetails(101L, 1L, 10L, "Mesa 1-A", "Cerca al bar", 6)).thenReturn(1);

    UpdateTableRequest request = new UpdateTableRequest(10L, "Mesa 1-A", "Cerca al bar", 6);

    tableService.updateTable(101L, 1L, request);

    verify(tableRepository).updateTableDetails(101L, 1L, 10L, "Mesa 1-A", "Cerca al bar", 6);
  }

  @Test
  @DisplayName("Should create zone in floor successfully")
  void shouldCreateZoneSuccessfully() {
    CreateZoneRequest request = new CreateZoneRequest(10L, "Karaoke", "Zona de cabinas y micrófonos");
    RestaurantFloor floor = new RestaurantFloor(10L, 1L, "Segundo Piso", null, null, null);
    when(floorRepository.findByIdAndBranchId(10L, 1L)).thenReturn(Optional.of(floor));
    when(zoneRepository.existsByFloorIdAndName(10L, "Karaoke")).thenReturn(false);

    RestaurantZone savedZone =
        new RestaurantZone(50L, 1L, 10L, "Karaoke", "Zona de cabinas y micrófonos", null, null);
    when(zoneRepository.save(any(RestaurantZone.class))).thenReturn(savedZone);

    Long zoneId = tableService.createZone(1L, request);

    assertThat(zoneId).isEqualTo(50L);
    verify(zoneRepository).save(any(RestaurantZone.class));
  }

  @Test
  @DisplayName("Should throw DuplicateKeyException when zone name already exists in floor")
  void shouldThrowWhenDuplicateZoneNameInFloor() {
    CreateZoneRequest request = new CreateZoneRequest(10L, "Juegos", null);
    RestaurantFloor floor = new RestaurantFloor(10L, 1L, "Segundo Piso", null, null, null);
    when(floorRepository.findByIdAndBranchId(10L, 1L)).thenReturn(Optional.of(floor));
    when(zoneRepository.existsByFloorIdAndName(10L, "Juegos")).thenReturn(true);

    assertThatThrownBy(() -> tableService.createZone(1L, request))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("Juegos");
  }

  @Test
  @DisplayName("Should update zone and sync tables floorId when zone moves to another floor")
  void shouldUpdateZoneAndSyncTablesFloorWhenFloorChanges() {
    RestaurantZone existingZone = new RestaurantZone(50L, 1L, 10L, "Juegos", "Vieja desc", null, null);
    when(zoneRepository.findByIdAndBranchId(50L, 1L)).thenReturn(Optional.of(existingZone));
    when(floorRepository.findByIdAndBranchId(20L, 1L))
        .thenReturn(Optional.of(new RestaurantFloor(20L, 1L, "Tercer Piso", null, null, null)));
    when(zoneRepository.existsByFloorIdAndName(20L, "Zona Arcade")).thenReturn(false);
    when(zoneRepository.updateZone(50L, 1L, 20L, "Zona Arcade", "Nueva desc")).thenReturn(1);

    UpdateZoneRequest request = new UpdateZoneRequest(20L, "Zona Arcade", "Nueva desc");

    tableService.updateZone(50L, 1L, request);

    verify(zoneRepository).updateZone(50L, 1L, 20L, "Zona Arcade", "Nueva desc");
    verify(tableRepository).updateFloorByZone(50L, 1L, 20L);
  }

  @Test
  @DisplayName("Should deactivate zone and its tables when no table is occupied")
  void shouldDeactivateZoneSuccessfully() {
    RestaurantZone existingZone = new RestaurantZone(50L, 1L, 10L, "Karaoke", null, null, null);
    when(zoneRepository.findByIdAndBranchId(50L, 1L)).thenReturn(Optional.of(existingZone));

    RestaurantTable table =
        new RestaurantTable(101L, 1L, 10L, 50L, "Mesa K1", null, TableState.AVAILABLE, 6, null, null);
    when(tableRepository.findAllByBranchIdAndZoneId(1L, 50L)).thenReturn(List.of(table));
    when(zoneRepository.softDeleteZone(50L, 1L)).thenReturn(1);

    tableService.deactivateZone(50L, 1L);

    verify(tableRepository).softDeleteTablesByZone(50L, 1L);
    verify(zoneRepository).softDeleteZone(50L, 1L);
  }

  @Test
  @DisplayName("Should throw IllegalStateException when deactivating zone with occupied tables")
  void shouldThrowWhenDeactivatingZoneWithOccupiedTables() {
    RestaurantZone existingZone = new RestaurantZone(50L, 1L, 10L, "Karaoke", null, null, null);
    when(zoneRepository.findByIdAndBranchId(50L, 1L)).thenReturn(Optional.of(existingZone));

    RestaurantTable occupiedTable =
        new RestaurantTable(101L, 1L, 10L, 50L, "Mesa K1", null, TableState.OCCUPIED, 6, null, null);
    when(tableRepository.findAllByBranchIdAndZoneId(1L, 50L)).thenReturn(List.of(occupiedTable));

    assertThatThrownBy(() -> tableService.deactivateZone(50L, 1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("occupied");
  }

  @Test
  @DisplayName("Should create table assigned to a zone and automatically infer floorId when omitted")
  void shouldCreateTableInZoneSuccessfully() {
    RestaurantZone zone = new RestaurantZone(50L, 1L, 10L, "Karaoke", null, null, null);
    RestaurantFloor floor = new RestaurantFloor(10L, 1L, "Segundo Piso", null, null, null);
    when(zoneRepository.findByIdAndBranchId(50L, 1L)).thenReturn(Optional.of(zone));
    when(floorRepository.findByIdAndBranchId(10L, 1L)).thenReturn(Optional.of(floor));
    when(tableRepository.existsByFloorIdAndName(10L, "Mesa K1")).thenReturn(false);

    RestaurantTable savedTable =
        new RestaurantTable(105L, 1L, 10L, 50L, "Mesa K1", "Frente al escenario", TableState.AVAILABLE, 6, null, null);
    when(tableRepository.save(any(RestaurantTable.class))).thenReturn(savedTable);

    // Passing floorId = null and zoneId = 50L: floorId 10L is inferred from the zone
    CreateTableRequest request =
        new CreateTableRequest(null, 50L, "Mesa K1", "Frente al escenario", 6);

    Long tableId = tableService.createTable(1L, request);

    assertThat(tableId).isEqualTo(105L);
    verify(tableRepository).save(any(RestaurantTable.class));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when creating table with zone that belongs to another floor")
  void shouldThrowWhenTableZoneDoesNotBelongToFloor() {
    RestaurantZone zoneOnFloor10 = new RestaurantZone(50L, 1L, 10L, "Karaoke", null, null, null);
    when(zoneRepository.findByIdAndBranchId(50L, 1L)).thenReturn(Optional.of(zoneOnFloor10));

    // Request specifies floorId = 99L, but zone 50L belongs to floor 10L
    CreateTableRequest request = new CreateTableRequest(99L, 50L, "Mesa K1", null, 4);

    assertThatThrownBy(() -> tableService.createTable(1L, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not belong to floor 99");
  }

  @Test
  @DisplayName("Should list tables filtered by zoneId")
  void shouldListTablesFilteredByZone() {
    RestaurantTable table =
        new RestaurantTable(105L, 1L, 10L, 50L, "Mesa K1", null, TableState.AVAILABLE, 6, null, null);
    when(tableRepository.findAllByBranchIdAndZoneId(1L, 50L)).thenReturn(List.of(table));

    List<RestaurantTable> result = tableService.getTables(1L, null, 50L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).zoneId()).isEqualTo(50L);
  }
}

