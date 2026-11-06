package com.facilcomanda.ponoa.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.catalog.CatalogPriceProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import com.facilcomanda.ponoa.printing.payload.PrinterFailureAlert;
import com.facilcomanda.ponoa.printing.payload.RegisterPrinterRequest;
import com.facilcomanda.ponoa.sales.CustomerOrderProvider;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  @Mock private CatalogPriceProvider catalogPriceProvider;
  @Mock private CustomerOrderProvider customerOrderProvider;
  @Mock private PrinterAlertPublisher alertPublisher;

  @InjectMocks private PrintJobService printJobService;

  @Test
  @DisplayName("Should register printer with terminalCode successfully")
  void shouldRegisterPrinter() {
    RegisterPrinterRequest request =
        new RegisterPrinterRequest("Caja 1", "192.168.1.100", 9100, "TCP", "CAJA-01");

    when(printerDeviceRepository.existsByBranchIdAndName(1L, "Caja 1")).thenReturn(false);

    PrinterDevice saved =
        new PrinterDevice(
            10L, 1L, "Caja 1", "192.168.1.100", 9100, "TCP", "CAJA-01", true, null, null);
    when(printerDeviceRepository.save(any(PrinterDevice.class))).thenReturn(saved);

    Long id = printJobService.registerPrinter(1L, request);

    assertThat(id).isEqualTo(10L);
    verify(printerDeviceRepository).save(any(PrinterDevice.class));
  }

  @Test
  @DisplayName("Should throw DuplicateKeyException when printer name already exists in branch")
  void shouldThrowWhenDuplicateNameInBranch() {
    RegisterPrinterRequest request =
        new RegisterPrinterRequest("Cocina 1", "192.168.1.100", 9100, "TCP", null);

    when(printerDeviceRepository.existsByBranchIdAndName(1L, "Cocina 1")).thenReturn(true);

    assertThatThrownBy(() -> printJobService.registerPrinter(1L, request))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("Cocina 1");
  }

  @Test
  @DisplayName("MANDATORY INVARIANT TEST: Two branches with the SAME printer IP must stay strictly isolated")
  void mandatoryBranchIsolationTestWithSamePrinterIp() {
    Long branchA = 1L;
    Long branchB = 2L;
    String sameIp = "192.168.1.100";

    PrinterDevice printerBranchA =
        new PrinterDevice(
            101L, branchA, "Cocina Branch A", sameIp, 9100, "TCP", null, true, null, null);
    PrinterDevice printerBranchB =
        new PrinterDevice(
            201L, branchB, "Cocina Branch B", sameIp, 9100, "TCP", null, true, null, null);

    PrinterRoute routeA =
        new PrinterRoute(1L, branchA, 101L, PrintDocumentType.KITCHEN_ORDER, "COCINA", null);

    when(printerRouteRepository.findMatchingRoutes(branchA, "KITCHEN_ORDER", "COCINA"))
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
            "ORDER-1001-AREA-COCINA-PRINTER-101",
            PrintJobStatus.PENDING,
            null,
            0,
            null,
            null);

    when(printJobRepository.save(any(PrintJob.class))).thenReturn(jobA);

    printJobService.enqueuePrintJobsForOrder(branchA, 1001L, PrintDocumentType.KITCHEN_ORDER);

    ArgumentCaptor<PrintJobMessage> messageCaptor = ArgumentCaptor.forClass(PrintJobMessage.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.1"),
            messageCaptor.capture());

    PrintJobMessage sentMessage = messageCaptor.getValue();
    assertThat(sentMessage.branchId()).isEqualTo(branchA);
    assertThat(sentMessage.printerId()).isEqualTo(101L);

    verify(rabbitTemplate, never())
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.2"),
            any(PrintJobMessage.class));
    verify(printerDeviceRepository, never()).findByIdAndBranchId(201L, branchB);
  }

  @Test
  @DisplayName("Multi-Station Kitchen Routing: partitions order between Grill and Kitchen stations cleanly")
  void shouldPartitionKitchenOrderAcrossMultipleStations() {
    Long branchId = 1L;
    Long orderId = 888L;
    Set<Long> skuIds = Set.of(10L, 20L);

    when(catalogPriceProvider.getProductionAreasForSkus(skuIds))
        .thenReturn(Map.of(10L, "COCINA", 20L, "PARRILLA"));

    PrinterDevice kitchenPrinter =
        new PrinterDevice(101L, branchId, "Ticketera Cocina", "192.168.1.50", 9100, "TCP", null, true, null, null);
    PrinterDevice grillPrinter =
        new PrinterDevice(102L, branchId, "Ticketera Parrilla", "192.168.1.51", 9100, "TCP", null, true, null, null);

    PrinterRoute routeKitchen =
        new PrinterRoute(1L, branchId, 101L, PrintDocumentType.KITCHEN_ORDER, "COCINA", null);
    PrinterRoute routeGrill =
        new PrinterRoute(2L, branchId, 102L, PrintDocumentType.KITCHEN_ORDER, "PARRILLA", null);

    when(printerRouteRepository.findMatchingRoutes(branchId, "KITCHEN_ORDER", "COCINA"))
        .thenReturn(List.of(routeKitchen));
    when(printerRouteRepository.findMatchingRoutes(branchId, "KITCHEN_ORDER", "PARRILLA"))
        .thenReturn(List.of(routeGrill));

    when(printerDeviceRepository.findByIdAndBranchId(101L, branchId))
        .thenReturn(Optional.of(kitchenPrinter));
    when(printerDeviceRepository.findByIdAndBranchId(102L, branchId))
        .thenReturn(Optional.of(grillPrinter));

    when(printJobRepository.save(any(PrintJob.class)))
        .thenAnswer(inv -> {
          PrintJob arg = inv.getArgument(0);
          return new PrintJob(
              arg.printerId() + 1000L,
              arg.branchId(),
              arg.printerId(),
              arg.orderId(),
              arg.documentType(),
              arg.idempotencyKey(),
              arg.status(),
              arg.errorMessage(),
              arg.retryCount(),
              null,
              null);
        });

    printJobService.enqueueKitchenPrintJobsForOrder(branchId, orderId, skuIds);

    ArgumentCaptor<PrintJobMessage> messageCaptor = ArgumentCaptor.forClass(PrintJobMessage.class);
    verify(rabbitTemplate, times(2))
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.1"),
            messageCaptor.capture());

    List<PrintJobMessage> sent = messageCaptor.getAllValues();
    assertThat(sent).hasSize(2);
    assertThat(sent)
        .extracting(PrintJobMessage::targetArea)
        .containsExactlyInAnyOrder("COCINA", "PARRILLA");
    assertThat(sent)
        .extracting(PrintJobMessage::printerId)
        .containsExactlyInAnyOrder(101L, 102L);
  }

  @Test
  @DisplayName("Targeted Cashier Printing: dispatches receipt strictly to terminal CAJA-01, never touching CAJA-02")
  void shouldRouteCashierReceiptStrictlyToTargetTerminal() {
    Long branchId = 1L;
    Long orderId = 777L;

    PrinterDevice caja1Printer =
        new PrinterDevice(
            201L, branchId, "Impresora Caja 1", "192.168.1.101", 9100, "TCP", "CAJA-01", true, null, null);

    when(printerDeviceRepository.findByBranchIdAndTerminalCodeAndIsActiveTrue(branchId, "CAJA-01"))
        .thenReturn(Optional.of(caja1Printer));

    when(printJobRepository.existsByIdempotencyKey(
        eq(branchId), eq(orderId), eq(201L), eq("CUSTOMER_RECEIPT"), any()))
        .thenReturn(false);

    PrintJob savedJob =
        new PrintJob(
            801L,
            branchId,
            201L,
            orderId,
            PrintDocumentType.CUSTOMER_RECEIPT,
            "CASHIER-CAJA-01-ORDER-777",
            PrintJobStatus.PENDING,
            null,
            0,
            null,
            null);
    when(printJobRepository.save(any(PrintJob.class))).thenReturn(savedJob);

    Long jobId = printJobService.enqueueCashierReceipt(branchId, orderId, "CAJA-01", null);

    assertThat(jobId).isEqualTo(801L);

    ArgumentCaptor<PrintJobMessage> messageCaptor = ArgumentCaptor.forClass(PrintJobMessage.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.1"),
            messageCaptor.capture());

    PrintJobMessage msg = messageCaptor.getValue();
    assertThat(msg.printerId()).isEqualTo(201L);
    assertThat(msg.ipAddress()).isEqualTo("192.168.1.101");
    assertThat(msg.targetArea()).isEqualTo("CAJA-01");

    // CAJA-02 was never queried
    verify(printerDeviceRepository, never())
        .findByBranchIdAndTerminalCodeAndIsActiveTrue(branchId, "CAJA-02");
  }

  @Test
  @DisplayName("Idempotency: duplicate cashier print request is ignored and does not double-enqueue")
  void shouldPreventDuplicatePrintJobsViaIdempotencyKey() {
    Long branchId = 1L;
    Long orderId = 777L;

    PrinterDevice caja1Printer =
        new PrinterDevice(
            201L, branchId, "Impresora Caja 1", "192.168.1.101", 9100, "TCP", "CAJA-01", true, null, null);

    when(printerDeviceRepository.findByBranchIdAndTerminalCodeAndIsActiveTrue(branchId, "CAJA-01"))
        .thenReturn(Optional.of(caja1Printer));

    when(printJobRepository.existsByIdempotencyKey(
        eq(branchId), eq(orderId), eq(201L), eq("CUSTOMER_RECEIPT"), any()))
        .thenReturn(true);

    Long result = printJobService.enqueueCashierReceipt(branchId, orderId, "CAJA-01", null);

    assertThat(result).isNull();
    verify(printJobRepository, never()).save(any(PrintJob.class));
    verify(rabbitTemplate, never())
        .convertAndSend(any(), any(), any(PrintJobMessage.class));
  }

  @Test
  @DisplayName("Cooldown Rule: throws PrintCooldownException when reprint is requested within 1 minute of last print")
  void shouldThrowCooldownExceptionWhenReprintingTooSoon() {
    Long branchId = 1L;
    Long orderId = 100L;

    PrintJob recentJob =
        new PrintJob(
            1L,
            branchId,
            10L,
            orderId,
            PrintDocumentType.KITCHEN_ORDER,
            "KEY-1",
            PrintJobStatus.COMPLETED,
            null,
            0,
            OffsetDateTime.now().minusSeconds(25),
            null);

    when(printJobRepository.findLatestJob(branchId, orderId, "KITCHEN_ORDER"))
        .thenReturn(Optional.of(recentJob));

    assertThatThrownBy(() -> printJobService.reprintKitchenOrder(branchId, orderId, null, null))
        .isInstanceOf(PrintCooldownException.class)
        .hasMessageContaining("Debes esperar al menos 1 minuto antes de realizar una reimpresion");

    verify(printJobRepository, never()).save(any(PrintJob.class));
    verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(PrintJobMessage.class));
  }

  @Test
  @DisplayName("Reprint Kitchen: enqueues reprint with isReprint=true and unique timestamp key after cooldown")
  void shouldReprintKitchenOrderSuccessfullyAfterCooldown() {
    Long branchId = 1L;
    Long orderId = 100L;

    PrintJob oldJob =
        new PrintJob(
            1L,
            branchId,
            10L,
            orderId,
            PrintDocumentType.KITCHEN_ORDER,
            "KEY-1",
            PrintJobStatus.COMPLETED,
            null,
            0,
            OffsetDateTime.now().minusSeconds(70),
            null);

    when(printJobRepository.findLatestJob(branchId, orderId, "KITCHEN_ORDER"))
        .thenReturn(Optional.of(oldJob));

    CustomerOrderProvider.OrderPrintSummary summary =
        new CustomerOrderProvider.OrderPrintSummary(
            orderId, branchId, 5L, "Mesa 5", "DINE_IN", "Sin sal", Set.of(10L));
    when(customerOrderProvider.getOrderPrintSummary(branchId, orderId))
        .thenReturn(Optional.of(summary));

    when(catalogPriceProvider.getProductionAreasForSkus(Set.of(10L)))
        .thenReturn(Map.of(10L, "COCINA"));

    PrinterDevice kitchenPrinter =
        new PrinterDevice(
            10L, branchId, "Ticketera Cocina", "192.168.1.50", 9100, "TCP", null, true, null, null);
    PrinterRoute route =
        new PrinterRoute(1L, branchId, 10L, PrintDocumentType.KITCHEN_ORDER, "COCINA", null);

    when(printerRouteRepository.findMatchingRoutes(branchId, "KITCHEN_ORDER", "COCINA"))
        .thenReturn(List.of(route));
    when(printerDeviceRepository.findByIdAndBranchId(10L, branchId))
        .thenReturn(Optional.of(kitchenPrinter));

    when(printJobRepository.save(any(PrintJob.class)))
        .thenAnswer(inv -> {
          PrintJob arg = inv.getArgument(0);
          return new PrintJob(
              999L,
              arg.branchId(),
              arg.printerId(),
              arg.orderId(),
              arg.documentType(),
              arg.idempotencyKey(),
              arg.status(),
              arg.errorMessage(),
              arg.retryCount(),
              null,
              null);
        });

    List<Long> result = printJobService.reprintKitchenOrder(branchId, orderId, null, null);

    assertThat(result).containsExactly(999L);

    ArgumentCaptor<PrintJobMessage> captor = ArgumentCaptor.forClass(PrintJobMessage.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.1"),
            captor.capture());

    PrintJobMessage sent = captor.getValue();
    assertThat(sent.isReprint()).isTrue();
    assertThat(sent.tableName()).isEqualTo("Mesa 5");
    assertThat(sent.notes()).isEqualTo("Sin sal");
  }

  @Test
  @DisplayName("Reprint Receipt: enqueues receipt reprint with isReprint=true")
  void shouldReprintReceiptSuccessfully() {
    Long branchId = 1L;
    Long orderId = 200L;

    when(printJobRepository.findLatestJob(branchId, orderId, "CUSTOMER_RECEIPT"))
        .thenReturn(Optional.empty());

    CustomerOrderProvider.OrderPrintSummary summary =
        new CustomerOrderProvider.OrderPrintSummary(
            orderId, branchId, 2L, "Mesa 2", "DINE_IN", null, Set.of());
    when(customerOrderProvider.getOrderPrintSummary(branchId, orderId))
        .thenReturn(Optional.of(summary));

    PrinterDevice cajaPrinter =
        new PrinterDevice(
            20L, branchId, "Caja 1", "192.168.1.100", 9100, "TCP", "CAJA-01", true, null, null);
    when(printerDeviceRepository.findByBranchIdAndTerminalCodeAndIsActiveTrue(branchId, "CAJA-01"))
        .thenReturn(Optional.of(cajaPrinter));

    when(printJobRepository.save(any(PrintJob.class)))
        .thenAnswer(inv -> {
          PrintJob arg = inv.getArgument(0);
          return new PrintJob(
              888L,
              arg.branchId(),
              arg.printerId(),
              arg.orderId(),
              arg.documentType(),
              arg.idempotencyKey(),
              arg.status(),
              arg.errorMessage(),
              arg.retryCount(),
              null,
              null);
        });

    Long jobId = printJobService.reprintCashierReceipt(branchId, orderId, "CAJA-01", null, "Cliente solicito copia");

    assertThat(jobId).isEqualTo(888L);

    ArgumentCaptor<PrintJobMessage> captor = ArgumentCaptor.forClass(PrintJobMessage.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.1"),
            captor.capture());

    PrintJobMessage sent = captor.getValue();
    assertThat(sent.isReprint()).isTrue();
    assertThat(sent.printerId()).isEqualTo(20L);
  }

  @Test
  @DisplayName("Retry Job: resets FAILED job and re-publishes to RabbitMQ")
  void shouldRetryFailedPrintJob() {
    Long branchId = 1L;
    Long jobId = 77L;

    PrintJob failedJob =
        new PrintJob(
            jobId,
            branchId,
            15L,
            300L,
            PrintDocumentType.KITCHEN_ORDER,
            "KEY-FAILED",
            PrintJobStatus.FAILED,
            "Connection timeout",
            2,
            null,
            null);

    when(printJobRepository.findByIdAndBranchId(jobId, branchId))
        .thenReturn(Optional.of(failedJob));

    PrinterDevice printer =
        new PrinterDevice(
            15L, branchId, "Cocina", "192.168.1.50", 9100, "TCP", null, true, null, null);
    when(printerDeviceRepository.findByIdAndBranchId(15L, branchId))
        .thenReturn(Optional.of(printer));

    CustomerOrderProvider.OrderPrintSummary summary =
        new CustomerOrderProvider.OrderPrintSummary(
            300L, branchId, 1L, "Mesa 1", "DINE_IN", null, Set.of());
    when(customerOrderProvider.getOrderPrintSummary(branchId, 300L))
        .thenReturn(Optional.of(summary));

    Long retriedId = printJobService.retryPrintJob(branchId, jobId);

    assertThat(retriedId).isEqualTo(jobId);
    verify(printJobRepository).resetJobForRetry(jobId, branchId);

    ArgumentCaptor<PrintJobMessage> captor = ArgumentCaptor.forClass(PrintJobMessage.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.1"),
            captor.capture());

    PrintJobMessage sent = captor.getValue();
    assertThat(sent.jobId()).isEqualTo(jobId);
    assertThat(sent.ipAddress()).isEqualTo("192.168.1.50");
  }

  @Test
  @DisplayName("Incremental Additions: enqueues print job ONLY to station of added item with isAddition=true")
  void shouldEnqueueAdditionOnlyToTargetStationWithAdditionBanner() {
    Long branchId = 1L;
    Long orderId = 999L;
    Set<Long> addedSkuIds = Set.of(20L);

    when(catalogPriceProvider.getProductionAreasForSkus(addedSkuIds))
        .thenReturn(Map.of(20L, "PARRILLA"));

    PrinterDevice grillPrinter =
        new PrinterDevice(102L, branchId, "Ticketera Parrilla", "192.168.1.51", 9100, "TCP", null, true, null, null);
    PrinterRoute routeGrill =
        new PrinterRoute(2L, branchId, 102L, PrintDocumentType.KITCHEN_ORDER, "PARRILLA", null);

    when(printerRouteRepository.findMatchingRoutes(branchId, "KITCHEN_ORDER", "PARRILLA"))
        .thenReturn(List.of(routeGrill));
    when(printerDeviceRepository.findByIdAndBranchId(102L, branchId))
        .thenReturn(Optional.of(grillPrinter));

    when(printJobRepository.save(any(PrintJob.class)))
        .thenAnswer(inv -> {
          PrintJob arg = inv.getArgument(0);
          return new PrintJob(
              777L,
              arg.branchId(),
              arg.printerId(),
              arg.orderId(),
              arg.documentType(),
              arg.idempotencyKey(),
              arg.status(),
              arg.errorMessage(),
              arg.retryCount(),
              null,
              null);
        });

    printJobService.enqueueKitchenPrintJobsForAddition(
        branchId, orderId, addedSkuIds, "Mesa 4", "DINE_IN", "Ronda 2: solo parrilla");

    ArgumentCaptor<PrintJobMessage> messageCaptor = ArgumentCaptor.forClass(PrintJobMessage.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.1"),
            messageCaptor.capture());

    PrintJobMessage sent = messageCaptor.getValue();
    assertThat(sent.printerId()).isEqualTo(102L);
    assertThat(sent.targetArea()).isEqualTo("PARRILLA");
    assertThat(sent.isAddition()).isTrue();
    assertThat(sent.isReprint()).isFalse();
    assertThat(sent.notes()).isEqualTo("Ronda 2: solo parrilla");
    assertThat(sent.tableName()).isEqualTo("Mesa 4");

    verify(printerRouteRepository, never())
        .findMatchingRoutes(branchId, "KITCHEN_ORDER", "COCINA");
  }

  @Test
  @DisplayName("Should request pre-check print, route to pre-check printer and publish message")
  void shouldPrintPreCheckSuccessfully() {
    Long branchId = 1L;
    Long orderId = 30L;

    CustomerOrderProvider.OrderPreCheckSummary summary =
        new CustomerOrderProvider.OrderPreCheckSummary(
            orderId,
            branchId,
            4L,
            "Mesa 4",
            "DINE_IN",
            "cajero@restaurant.com",
            new java.math.BigDecimal("118.00"),
            new java.math.BigDecimal("100.00"),
            new java.math.BigDecimal("18.00"),
            new java.math.BigDecimal("11.80"),
            List.of());

    when(customerOrderProvider.getOrderPreCheckSummary(branchId, orderId))
        .thenReturn(Optional.of(summary));
    when(printJobRepository.findLatestJob(branchId, orderId, "PRE_CHECK"))
        .thenReturn(Optional.empty());

    PrinterDevice preCheckPrinter =
        new PrinterDevice(105L, branchId, "Caja Principal", "192.168.1.55", 9100, "TCP", null, true, null, null);
    PrinterRoute route =
        new PrinterRoute(10L, branchId, 105L, PrintDocumentType.PRE_CHECK, null, null);

    when(printerRouteRepository.findAllByBranchIdAndDocumentType(branchId, PrintDocumentType.PRE_CHECK))
        .thenReturn(List.of(route));
    when(printerDeviceRepository.findByIdAndBranchId(105L, branchId))
        .thenReturn(Optional.of(preCheckPrinter));

    when(printJobRepository.save(any(PrintJob.class)))
        .thenAnswer(inv -> {
          PrintJob arg = inv.getArgument(0);
          return new PrintJob(
              888L,
              arg.branchId(),
              arg.printerId(),
              arg.orderId(),
              arg.documentType(),
              arg.idempotencyKey(),
              arg.status(),
              arg.errorMessage(),
              arg.retryCount(),
              null,
              null);
        });

    Long jobId = printJobService.requestPreCheckPrint(branchId, orderId, null);

    assertThat(jobId).isEqualTo(888L);

    ArgumentCaptor<PrintJobMessage> captor = ArgumentCaptor.forClass(PrintJobMessage.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(PrintingQueueConfig.PRINTING_EXCHANGE),
            eq("printing.branch.1"),
            captor.capture());

    PrintJobMessage sent = captor.getValue();
    assertThat(sent.jobId()).isEqualTo(888L);
    assertThat(sent.documentType()).isEqualTo(PrintDocumentType.PRE_CHECK);
    assertThat(sent.printerId()).isEqualTo(105L);
    assertThat(sent.tableName()).isEqualTo("Mesa 4");
    assertThat(sent.isReprint()).isFalse();
  }

  @Test
  @DisplayName("Should enforce reprint cooldown if pre-check was already printed within 60s")
  void shouldEnforceCooldownOnPreCheckReprint() {
    Long branchId = 1L;
    Long orderId = 30L;

    CustomerOrderProvider.OrderPreCheckSummary summary =
        new CustomerOrderProvider.OrderPreCheckSummary(
            orderId,
            branchId,
            4L,
            "Mesa 4",
            "DINE_IN",
            "cajero@restaurant.com",
            new java.math.BigDecimal("118.00"),
            new java.math.BigDecimal("100.00"),
            new java.math.BigDecimal("18.00"),
            new java.math.BigDecimal("11.80"),
            List.of());

    when(customerOrderProvider.getOrderPreCheckSummary(branchId, orderId))
        .thenReturn(Optional.of(summary));

    PrintJob recentJob =
        new PrintJob(
            888L,
            branchId,
            105L,
            orderId,
            PrintDocumentType.PRE_CHECK,
            "key",
            PrintJobStatus.COMPLETED,
            null,
            0,
            OffsetDateTime.now().minusSeconds(10),
            null);

    when(printJobRepository.findLatestJob(branchId, orderId, "PRE_CHECK"))
        .thenReturn(Optional.of(recentJob));

    assertThatThrownBy(() -> printJobService.requestPreCheckPrint(branchId, orderId, null))
        .isInstanceOf(PrintCooldownException.class)
        .hasMessageContaining("reimpresion");
  }

  @Test
  @DisplayName("ACK: Should update status to COMPLETED when status is COMPLETED")
  void shouldAcknowledgeJobSuccessfullyWhenCompleted() {
    Long branchId = 1L;
    Long jobId = 100L;

    PrintJob pendingJob =
        new PrintJob(
            jobId,
            branchId,
            10L,
            50L,
            PrintDocumentType.KITCHEN_ORDER,
            "key-100",
            PrintJobStatus.DISPATCHED,
            null,
            0,
            null,
            null);

    when(printJobRepository.findByIdAndBranchId(jobId, branchId))
        .thenReturn(Optional.of(pendingJob));

    printJobService.acknowledgePrintJob(branchId, jobId, PrintJobStatus.COMPLETED, null);

    verify(printJobRepository)
        .updateJobStatus(jobId, branchId, PrintJobStatus.COMPLETED.name(), null);
    verify(alertPublisher, never()).publishPrinterFailure(any());
  }

  @Test
  @DisplayName("ACK: Should update status to FAILED and publish PrinterFailureAlert when status is FAILED")
  void shouldAcknowledgeJobSuccessfullyWhenFailedAndPublishAlert() {
    Long branchId = 1L;
    Long jobId = 101L;

    PrintJob dispatchedJob =
        new PrintJob(
            jobId,
            branchId,
            10L,
            50L,
            PrintDocumentType.CUSTOMER_RECEIPT,
            "key-101",
            PrintJobStatus.DISPATCHED,
            null,
            0,
            null,
            null);

    PrinterDevice device =
        new PrinterDevice(
            10L, branchId, "Caja 1", "192.168.1.100", 9100, "TCP", "CAJA-01", true, null, null);

    when(printJobRepository.findByIdAndBranchId(jobId, branchId))
        .thenReturn(Optional.of(dispatchedJob));
    when(printerDeviceRepository.findByIdAndBranchId(10L, branchId))
        .thenReturn(Optional.of(device));

    printJobService.acknowledgePrintJob(
        branchId, jobId, PrintJobStatus.FAILED, "Out of paper");

    verify(printJobRepository)
        .updateJobStatus(jobId, branchId, PrintJobStatus.FAILED.name(), "Out of paper");

    ArgumentCaptor<PrinterFailureAlert> alertCaptor =
        ArgumentCaptor.forClass(PrinterFailureAlert.class);
    verify(alertPublisher).publishPrinterFailure(alertCaptor.capture());

    PrinterFailureAlert alert = alertCaptor.getValue();
    assertThat(alert.jobId()).isEqualTo(jobId);
    assertThat(alert.branchId()).isEqualTo(branchId);
    assertThat(alert.ipAddress()).isEqualTo("192.168.1.100");
    assertThat(alert.port()).isEqualTo(9100);
    assertThat(alert.errorMessage()).isEqualTo("Out of paper");
  }

  @Test
  @DisplayName("ACK: Should ignore ACK when job is already COMPLETED (idempotent)")
  void shouldIgnoreAckWhenJobAlreadyCompleted() {
    Long branchId = 1L;
    Long jobId = 102L;

    PrintJob completedJob =
        new PrintJob(
            jobId,
            branchId,
            10L,
            50L,
            PrintDocumentType.KITCHEN_ORDER,
            "key-102",
            PrintJobStatus.COMPLETED,
            null,
            0,
            null,
            null);

    when(printJobRepository.findByIdAndBranchId(jobId, branchId))
        .thenReturn(Optional.of(completedJob));

    printJobService.acknowledgePrintJob(branchId, jobId, PrintJobStatus.COMPLETED, null);

    verify(printJobRepository, never()).updateJobStatus(any(), any(), any(), any());
    verify(alertPublisher, never()).publishPrinterFailure(any());
  }

  @Test
  @DisplayName("ACK: Should throw IllegalArgumentException when status is not COMPLETED or FAILED")
  void shouldThrowWhenAckStatusIsInvalid() {
    Long branchId = 1L;
    Long jobId = 103L;

    PrintJob job =
        new PrintJob(
            jobId,
            branchId,
            10L,
            50L,
            PrintDocumentType.KITCHEN_ORDER,
            "key-103",
            PrintJobStatus.DISPATCHED,
            null,
            0,
            null,
            null);

    when(printJobRepository.findByIdAndBranchId(jobId, branchId)).thenReturn(Optional.of(job));

    assertThatThrownBy(
            () ->
                printJobService.acknowledgePrintJob(
                    branchId, jobId, PrintJobStatus.PENDING, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected COMPLETED or FAILED");
  }

  @Test
  @DisplayName("ACK: Should throw ResourceNotFoundException when job does not exist")
  void shouldThrowWhenAckJobNotFound() {
    Long branchId = 1L;
    Long jobId = 9999L;

    when(printJobRepository.findByIdAndBranchId(jobId, branchId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                printJobService.acknowledgePrintJob(
                    branchId, jobId, PrintJobStatus.COMPLETED, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("PrintJob 9999 not found");
  }

  @Test
  @DisplayName("Should return jobs filtered by status when status is provided")
  void shouldReturnJobsFilteredByStatus() {
    Long branchId = 1L;
    PrintJob job =
        new PrintJob(
            1L,
            branchId,
            10L,
            50L,
            PrintDocumentType.KITCHEN_ORDER,
            "key-1",
            PrintJobStatus.FAILED,
            "Error",
            1,
            null,
            null);

    when(printJobRepository.findAllByBranchIdAndStatus(branchId, PrintJobStatus.FAILED))
        .thenReturn(List.of(job));

    List<PrintJob> result = printJobService.getPrintJobsByBranch(branchId, PrintJobStatus.FAILED);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).status()).isEqualTo(PrintJobStatus.FAILED);
    verify(printJobRepository).findAllByBranchIdAndStatus(branchId, PrintJobStatus.FAILED);
  }

  @Test
  @DisplayName("Should return all jobs for branch when status is null")
  void shouldReturnAllJobsWhenStatusIsNull() {
    Long branchId = 1L;
    PrintJob job1 =
        new PrintJob(
            1L,
            branchId,
            10L,
            50L,
            PrintDocumentType.KITCHEN_ORDER,
            "key-1",
            PrintJobStatus.COMPLETED,
            null,
            0,
            null,
            null);
    PrintJob job2 =
        new PrintJob(
            2L,
            branchId,
            10L,
            51L,
            PrintDocumentType.CUSTOMER_RECEIPT,
            "key-2",
            PrintJobStatus.DISPATCHED,
            null,
            0,
            null,
            null);

    when(printJobRepository.findAllByBranchId(branchId)).thenReturn(List.of(job1, job2));

    List<PrintJob> result = printJobService.getPrintJobsByBranch(branchId, null);

    assertThat(result).hasSize(2);
    verify(printJobRepository).findAllByBranchId(branchId);
  }
}
