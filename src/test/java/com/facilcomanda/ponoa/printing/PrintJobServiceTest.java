package com.facilcomanda.ponoa.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import com.facilcomanda.ponoa.printing.payload.RegisterPrinterRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class PrintJobServiceTest {

  @Mock private PrinterDeviceRepository printerDeviceRepository;
  @Mock private PrinterRouteRepository printerRouteRepository;
  @Mock private PrintJobRepository printJobRepository;
  @Mock private RabbitTemplate rabbitTemplate;

  @InjectMocks private PrintJobService printJobService;

  @Test
  @DisplayName("Should register printer for branch successfully")
  void shouldRegisterPrinter() {
    RegisterPrinterRequest request =
        new RegisterPrinterRequest("Cocina 1", "192.168.1.100", 9100, "TCP");

    when(printerDeviceRepository.existsByBranchIdAndName(1L, "Cocina 1")).thenReturn(false);

    PrinterDevice saved =
        new PrinterDevice(10L, 1L, "Cocina 1", "192.168.1.100", 9100, "TCP", true, null, null);
    when(printerDeviceRepository.save(any(PrinterDevice.class))).thenReturn(saved);

    Long id = printJobService.registerPrinter(1L, request);

    assertThat(id).isEqualTo(10L);
    verify(printerDeviceRepository).save(any(PrinterDevice.class));
  }

  @Test
  @DisplayName("Should throw DuplicateKeyException when printer name already exists in branch")
  void shouldThrowWhenDuplicateNameInBranch() {
    RegisterPrinterRequest request =
        new RegisterPrinterRequest("Cocina 1", "192.168.1.100", 9100, "TCP");

    when(printerDeviceRepository.existsByBranchIdAndName(1L, "Cocina 1")).thenReturn(true);

    assertThatThrownBy(() -> printJobService.registerPrinter(1L, request))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("Cocina 1");
  }

  @Test
  @DisplayName("MANDATORY INVARIANT TEST: Two branches with the SAME printer IP must stay strictly isolated")
  void mandatoryBranchIsolationTestWithSamePrinterIp() {
    // Both Branch A (1L) and Branch B (2L) have private LAN printer at identical IP 192.168.1.100
    Long branchA = 1L;
    Long branchB = 2L;
    String sameIp = "192.168.1.100";

    PrinterDevice printerBranchA =
        new PrinterDevice(101L, branchA, "Cocina Branch A", sameIp, 9100, "TCP", true, null, null);
    PrinterDevice printerBranchB =
        new PrinterDevice(201L, branchB, "Cocina Branch B", sameIp, 9100, "TCP", true, null, null);

    PrinterRoute routeA =
        new PrinterRoute(1L, branchA, 101L, PrintDocumentType.KITCHEN_ORDER, null);

    when(printerRouteRepository.findAllByBranchIdAndDocumentType(branchA, PrintDocumentType.KITCHEN_ORDER))
        .thenReturn(List.of(routeA));

    when(printerDeviceRepository.findByIdAndBranchId(101L, branchA))
        .thenReturn(Optional.of(printerBranchA));

    PrintJob jobA =
        new PrintJob(
            501L,
            branchA,
            101L,
            1001L,
            PrintDocumentType.KITCHEN_ORDER,
            PrintJobStatus.PENDING,
            null,
            0,
            null,
            null);

    when(printJobRepository.save(any(PrintJob.class))).thenReturn(jobA);

    // Trigger printing for Branch A
    printJobService.enqueuePrintJobsForOrder(branchA, 1001L, PrintDocumentType.KITCHEN_ORDER);

    // Assert message sent to Branch A's queue only
    ArgumentCaptor<PrintJobMessage> messageCaptor = ArgumentCaptor.forClass(PrintJobMessage.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.1"),
            messageCaptor.capture());

    PrintJobMessage sentMessage = messageCaptor.getValue();
    assertThat(sentMessage.branchId()).isEqualTo(branchA);
    assertThat(sentMessage.printerId()).isEqualTo(101L);

    // Ensure Branch B's printer and routing key were NEVER touched
    verify(rabbitTemplate, never())
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.2"),
            any(PrintJobMessage.class));
    verify(printerDeviceRepository, never()).findByIdAndBranchId(201L, branchB);
  }
}
