package com.facilcomanda.ponoa.printing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface PrinterDeviceRepository extends CrudRepository<PrinterDevice, Long> {

  Optional<PrinterDevice> findByIdAndBranchId(Long id, Long branchId);

  List<PrinterDevice> findAllByBranchId(Long branchId);

  List<PrinterDevice> findAllByBranchIdAndIsActiveTrue(Long branchId);

  Optional<PrinterDevice> findByBranchIdAndTerminalCodeAndIsActiveTrue(
      Long branchId, String terminalCode);

  boolean existsByBranchIdAndName(Long branchId, String name);

  @Modifying
  @Query(
      """
      UPDATE printer_devices 
      SET name = :name, 
          ip_address = :ipAddress, 
          port = :port, 
          connection_type = :connectionType, 
          terminal_code = :terminalCode, 
          is_active = :isActive, 
          updated_at = NOW() 
      WHERE id = :id AND branch_id = :branchId
      """)
  int updatePrinterDetails(
      @Param("id") Long id,
      @Param("branchId") Long branchId,
      @Param("name") String name,
      @Param("ipAddress") String ipAddress,
      @Param("port") Integer port,
      @Param("connectionType") String connectionType,
      @Param("terminalCode") String terminalCode,
      @Param("isActive") Boolean isActive);

  @Modifying
  @Query("UPDATE printer_devices SET is_active = FALSE, updated_at = NOW() WHERE id = :id AND branch_id = :branchId")
  int softDeletePrinter(@Param("id") Long id, @Param("branchId") Long branchId);
}
