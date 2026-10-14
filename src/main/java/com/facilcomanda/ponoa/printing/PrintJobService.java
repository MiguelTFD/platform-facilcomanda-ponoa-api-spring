package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.catalog.CatalogPriceProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import com.facilcomanda.ponoa.printing.payload.RegisterPrinterRequest;
import com.facilcomanda.ponoa.sales.CustomerOrderProvider;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PrintJobService {

  public static final long REPRINT_COOLDOWN_SECONDS = 60;

  private final PrinterDeviceRepository printerDeviceRepository;
  private final PrinterRouteRepository printerRouteRepository;
  private final PrintJobRepository printJobRepository;
  private final RabbitTemplate rabbitTemplate;
  private final CatalogPriceProvider catalogPriceProvider;
  private final CustomerOrderProvider customerOrderProvider;

  PrintJobService(
      PrinterDeviceRepository printerDeviceRepository,
      PrinterRouteRepository printerRouteRepository,
      PrintJobRepository printJobRepository,
      RabbitTemplate rabbitTemplate,
      CatalogPriceProvider catalogPriceProvider,
      CustomerOrderProvider customerOrderProvider) {
    this.printerDeviceRepository = printerDeviceRepository;
    this.printerRouteRepository = printerRouteRepository;
    this.printJobRepository = printJobRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.catalogPriceProvider = catalogPriceProvider;
    this.customerOrderProvider = customerOrderProvider;
  }

  @Transactional
  public Long registerPrinter(Long branchId, RegisterPrinterRequest request) {
    if (printerDeviceRepository.existsByBranchIdAndName(branchId, request.name())) {
      throw new DuplicateKeyException(
          "Printer with name " + request.name() + " already exists in branch " + branchId);
    }

    PrinterDevice device =
        new PrinterDevice(
            null,
            branchId,
            request.name(),
            request.ipAddress(),
            request.port(),
            request.connectionType() != null ? request.connectionType() : "TCP",
            request.terminalCode(),
            true,
            null,
            null);

    return printerDeviceRepository.save(device).id();
  }

  @Transactional
  public void updatePrinter(
      Long branchId, Long printerId, com.facilcomanda.ponoa.printing.payload.UpdatePrinterRequest request) {
    PrinterDevice device =
        printerDeviceRepository
            .findByIdAndBranchId(printerId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Printer " + printerId + " not found in branch " + branchId));

    if (!device.name().equalsIgnoreCase(request.name().trim())
        && printerDeviceRepository.existsByBranchIdAndName(branchId, request.name().trim())) {
      throw new DuplicateKeyException(
          "Printer with name " + request.name() + " already exists in branch " + branchId);
    }

    PrinterDevice updated =
        new PrinterDevice(
            device.id(),
            device.branchId(),
            request.name().trim(),
            request.ipAddress().trim(),
            request.port() != null ? request.port() : device.port(),
            request.connectionType() != null ? request.connectionType().trim() : device.connectionType(),
            request.terminalCode() != null ? request.terminalCode().trim() : device.terminalCode(),
            request.isActive() != null ? request.isActive() : device.isActive(),
            device.createdAt(),
            OffsetDateTime.now());

    printerDeviceRepository.save(updated);
  }

  @Transactional
  public void deletePrinter(Long branchId, Long printerId) {
    PrinterDevice device =
        printerDeviceRepository
            .findByIdAndBranchId(printerId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Printer " + printerId + " not found in branch " + branchId));

    PrinterDevice updated =
        new PrinterDevice(
            device.id(),
            device.branchId(),
            device.name(),
            device.ipAddress(),
            device.port(),
            device.connectionType(),
            device.terminalCode(),
            false,
            device.createdAt(),
            OffsetDateTime.now());

    printerDeviceRepository.save(updated);
  }

  @Transactional
  public void configureRoute(
      Long branchId, Long printerId, PrintDocumentType documentType, String productionArea) {
    printerDeviceRepository
        .findByIdAndBranchId(printerId, branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Printer " + printerId + " not found in branch " + branchId));

    if (printerRouteRepository.existsByBranchIdAndPrinterIdAndDocumentTypeAndProductionArea(
        branchId, printerId, documentType, productionArea)) {
      return; // Already configured
    }

    PrinterRoute route =
        new PrinterRoute(null, branchId, printerId, documentType, productionArea, null);
    printerRouteRepository.save(route);
  }

  @Transactional
  public void configureRoute(Long branchId, Long printerId, PrintDocumentType documentType) {
    configureRoute(branchId, printerId, documentType, null);
  }

  @Transactional
  public void enqueueKitchenPrintJobsForOrder(
      Long branchId, Long orderId, Set<Long> skuIds) {
    enqueueKitchenPrintJobsForOrder(branchId, orderId, skuIds, null, "DINE_IN", null);
  }

  @Transactional
  public void enqueueKitchenPrintJobsForOrder(
      Long branchId,
      Long orderId,
      Set<Long> skuIds,
      String tableName,
      String orderType,
      String notes) {
    Set<String> productionAreas;
    if (skuIds != null && !skuIds.isEmpty()) {
      Map<Long, String> areaMap = catalogPriceProvider.getProductionAreasForSkus(skuIds);
      productionAreas = new HashSet<>(areaMap.values());
      if (productionAreas.isEmpty()) {
        productionAreas.add("COCINA");
      }
    } else {
      productionAreas = Set.of("COCINA");
    }

    for (String area : productionAreas) {
      List<PrinterRoute> matchingRoutes =
          printerRouteRepository.findMatchingRoutes(
              branchId, PrintDocumentType.KITCHEN_ORDER.name(), area);

      for (PrinterRoute route : matchingRoutes) {
        PrinterDevice printer =
            printerDeviceRepository
                .findByIdAndBranchId(route.printerId(), branchId)
                .filter(p -> Boolean.TRUE.equals(p.isActive()))
                .orElse(null);

        if (printer == null) {
          continue;
        }

        String idempotencyKey =
            "ORDER-" + orderId + "-AREA-" + area + "-PRINTER-" + printer.id();

        if (printJobRepository.existsByIdempotencyKey(
            branchId,
            orderId,
            printer.id(),
            PrintDocumentType.KITCHEN_ORDER.name(),
            idempotencyKey)) {
          continue;
        }

        PrintJob job =
            new PrintJob(
                null,
                branchId,
                printer.id(),
                orderId,
                PrintDocumentType.KITCHEN_ORDER,
                idempotencyKey,
                PrintJobStatus.PENDING,
                null,
                0,
                null,
                null);

        try {
          PrintJob savedJob = printJobRepository.save(job);

          PrintJobMessage message =
              new PrintJobMessage(
                  savedJob.id(),
                  branchId,
                  printer.id(),
                  orderId,
                  PrintDocumentType.KITCHEN_ORDER,
                  printer.ipAddress(),
                  printer.port(),
                  area,
                  tableName,
                  orderType,
                  notes);

          String routingKey = "printing.branch." + branchId;
          rabbitTemplate.convertAndSend(
              PrintingQueueConfig.PRINTING_EXCHANGE, routingKey, message);
        } catch (DuplicateKeyException ex) {
          // Idempotent duplicate safely ignored
        }
      }
    }
  }

  @Transactional
  public void enqueueKitchenPrintJobsForAddition(
      Long branchId,
      Long orderId,
      Set<Long> addedSkuIds,
      String tableName,
      String orderType,
      String additionNotes) {
    Set<String> productionAreas;
    if (addedSkuIds != null && !addedSkuIds.isEmpty()) {
      Map<Long, String> areaMap = catalogPriceProvider.getProductionAreasForSkus(addedSkuIds);
      productionAreas = new HashSet<>(areaMap.values());
      if (productionAreas.isEmpty()) {
        productionAreas.add("COCINA");
      }
    } else {
      productionAreas = Set.of("COCINA");
    }

    for (String area : productionAreas) {
      List<PrinterRoute> matchingRoutes =
          printerRouteRepository.findMatchingRoutes(
              branchId, PrintDocumentType.KITCHEN_ORDER.name(), area);

      for (PrinterRoute route : matchingRoutes) {
        PrinterDevice printer =
            printerDeviceRepository
                .findByIdAndBranchId(route.printerId(), branchId)
                .filter(p -> Boolean.TRUE.equals(p.isActive()))
                .orElse(null);

        if (printer == null) {
          continue;
        }

        String idempotencyKey =
            "ORDER-"
                + orderId
                + "-ADDITION-AREA-"
                + area
                + "-PRINTER-"
                + printer.id()
                + "-TS-"
                + System.currentTimeMillis();

        PrintJob job =
            new PrintJob(
                null,
                branchId,
                printer.id(),
                orderId,
                PrintDocumentType.KITCHEN_ORDER,
                idempotencyKey,
                PrintJobStatus.PENDING,
                null,
                0,
                null,
                null);

        try {
          PrintJob savedJob = printJobRepository.save(job);

          PrintJobMessage message =
              new PrintJobMessage(
                  savedJob.id(),
                  branchId,
                  printer.id(),
                  orderId,
                  PrintDocumentType.KITCHEN_ORDER,
                  printer.ipAddress(),
                  printer.port(),
                  area,
                  tableName,
                  orderType,
                  additionNotes,
                  false,
                  true);

          String routingKey = "printing.branch." + branchId;
          rabbitTemplate.convertAndSend(
              PrintingQueueConfig.PRINTING_EXCHANGE, routingKey, message);
        } catch (DuplicateKeyException ex) {
          // Idempotent duplicate safely ignored
        }
      }
    }
  }

  @Transactional
  public void enqueuePrintJobsForOrder(
      Long branchId, Long orderId, PrintDocumentType documentType) {
    if (PrintDocumentType.KITCHEN_ORDER.equals(documentType)) {
      enqueueKitchenPrintJobsForOrder(branchId, orderId, Set.of());
      return;
    }

    List<PrinterRoute> routes =
        printerRouteRepository.findAllByBranchIdAndDocumentType(branchId, documentType);

    for (PrinterRoute route : routes) {
      PrinterDevice printer =
          printerDeviceRepository
              .findByIdAndBranchId(route.printerId(), branchId)
              .filter(p -> Boolean.TRUE.equals(p.isActive()))
              .orElse(null);

      if (printer == null) {
        continue;
      }

      String idempotencyKey =
          "ORDER-" + orderId + "-DOC-" + documentType.name() + "-PRINTER-" + printer.id();

      if (printJobRepository.existsByIdempotencyKey(
          branchId, orderId, printer.id(), documentType.name(), idempotencyKey)) {
        continue;
      }

      PrintJob job =
          new PrintJob(
              null,
              branchId,
              printer.id(),
              orderId,
              documentType,
              idempotencyKey,
              PrintJobStatus.PENDING,
              null,
              0,
              null,
              null);

      try {
        PrintJob savedJob = printJobRepository.save(job);

        PrintJobMessage message =
            new PrintJobMessage(
                savedJob.id(),
                branchId,
                printer.id(),
                orderId,
                documentType,
                printer.ipAddress(),
                printer.port(),
                null);

        String routingKey = "printing.branch." + branchId;
        rabbitTemplate.convertAndSend(
            PrintingQueueConfig.PRINTING_EXCHANGE, routingKey, message);
      } catch (DuplicateKeyException ex) {
        // Idempotent duplicate safely ignored
      }
    }
  }

  @Transactional
  public Long enqueueCashierReceipt(
      Long branchId, Long orderId, String terminalCode, String clientProvidedIdempotencyKey) {
    PrinterDevice printer = null;

    if (terminalCode != null && !terminalCode.isBlank()) {
      printer =
          printerDeviceRepository
              .findByBranchIdAndTerminalCodeAndIsActiveTrue(branchId, terminalCode)
              .orElse(null);
    }

    if (printer == null) {
      // Fallback: look for routes configured for CUSTOMER_RECEIPT
      List<PrinterRoute> routes =
          printerRouteRepository.findAllByBranchIdAndDocumentType(
              branchId, PrintDocumentType.CUSTOMER_RECEIPT);

      for (PrinterRoute route : routes) {
        printer =
            printerDeviceRepository
                .findByIdAndBranchId(route.printerId(), branchId)
                .filter(p -> Boolean.TRUE.equals(p.isActive()))
                .orElse(null);
        if (printer != null) {
          break;
        }
      }
    }

    if (printer == null) {
      throw new ResourceNotFoundException(
          "No active printer found for cashier terminal '"
              + terminalCode
              + "' or document CUSTOMER_RECEIPT in branch "
              + branchId);
    }

    String idempotencyKey =
        (clientProvidedIdempotencyKey != null && !clientProvidedIdempotencyKey.isBlank())
            ? clientProvidedIdempotencyKey
            : "CASHIER-" + (terminalCode != null ? terminalCode : "DEFAULT") + "-ORDER-" + orderId;

    if (printJobRepository.existsByIdempotencyKey(
        branchId,
        orderId,
        printer.id(),
        PrintDocumentType.CUSTOMER_RECEIPT.name(),
        idempotencyKey)) {
      return null;
    }

    PrintJob job =
        new PrintJob(
            null,
            branchId,
            printer.id(),
            orderId,
            PrintDocumentType.CUSTOMER_RECEIPT,
            idempotencyKey,
            PrintJobStatus.PENDING,
            null,
            0,
            null,
            null);

    try {
      PrintJob savedJob = printJobRepository.save(job);

      PrintJobMessage message =
          new PrintJobMessage(
              savedJob.id(),
              branchId,
              printer.id(),
              orderId,
              PrintDocumentType.CUSTOMER_RECEIPT,
              printer.ipAddress(),
              printer.port(),
              terminalCode);

      String routingKey = "printing.branch." + branchId;
      rabbitTemplate.convertAndSend(
          PrintingQueueConfig.PRINTING_EXCHANGE, routingKey, message);

      return savedJob.id();
    } catch (DuplicateKeyException ex) {
      return null;
    }
  }

  @Transactional(readOnly = true)
  public List<PrinterDevice> getPrinters(Long branchId) {
    return printerDeviceRepository.findAllByBranchId(branchId);
  }

  @Transactional(readOnly = true)
  public List<PrintJob> getPrintJobsByBranch(Long branchId, PrintJobStatus status) {
    if (status != null) {
      return printJobRepository.findAllByBranchIdAndStatus(branchId, status);
    }
    return printJobRepository.findAllByBranchIdAndStatus(branchId, PrintJobStatus.PENDING);
  }

  private void validateReprintCooldown(
      Long branchId, Long orderId, PrintDocumentType documentType) {
    printJobRepository
        .findLatestJob(branchId, orderId, documentType.name())
        .ifPresent(
            latestJob -> {
              if (latestJob.createdAt() != null) {
                long secondsElapsed =
                    Duration.between(latestJob.createdAt(), OffsetDateTime.now()).toSeconds();
                if (secondsElapsed < REPRINT_COOLDOWN_SECONDS) {
                  long secondsRemaining = REPRINT_COOLDOWN_SECONDS - secondsElapsed;
                  throw new PrintCooldownException(secondsRemaining);
                }
              }
            });
  }

  @Transactional
  public List<Long> reprintKitchenOrder(
      Long branchId, Long orderId, String targetProductionArea, Long targetPrinterId) {
    validateReprintCooldown(branchId, orderId, PrintDocumentType.KITCHEN_ORDER);

    CustomerOrderProvider.OrderPrintSummary orderSummary =
        customerOrderProvider
            .getOrderPrintSummary(branchId, orderId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    List<PrinterDevice> printersToPrint = new ArrayList<>();
    Map<Long, String> printerAreaMap = new HashMap<>();

    if (targetPrinterId != null) {
      PrinterDevice targetPrinter =
          printerDeviceRepository
              .findByIdAndBranchId(targetPrinterId, branchId)
              .filter(p -> Boolean.TRUE.equals(p.isActive()))
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Printer " + targetPrinterId + " not found or inactive in branch " + branchId));
      printersToPrint.add(targetPrinter);
      printerAreaMap.put(
          targetPrinter.id(),
          (targetProductionArea != null && !targetProductionArea.isBlank())
              ? targetProductionArea
              : "COCINA");
    } else {
      Set<String> areas;
      if (targetProductionArea != null && !targetProductionArea.isBlank()) {
        areas = Set.of(targetProductionArea);
      } else if (orderSummary.skuIds() != null && !orderSummary.skuIds().isEmpty()) {
        Map<Long, String> areaMap =
            catalogPriceProvider.getProductionAreasForSkus(orderSummary.skuIds());
        areas = new HashSet<>(areaMap.values());
        if (areas.isEmpty()) {
          areas.add("COCINA");
        }
      } else {
        areas = Set.of("COCINA");
      }

      for (String area : areas) {
        List<PrinterRoute> matchingRoutes =
            printerRouteRepository.findMatchingRoutes(
                branchId, PrintDocumentType.KITCHEN_ORDER.name(), area);

        for (PrinterRoute route : matchingRoutes) {
          printerDeviceRepository
              .findByIdAndBranchId(route.printerId(), branchId)
              .filter(p -> Boolean.TRUE.equals(p.isActive()))
              .ifPresent(
                  p -> {
                    printersToPrint.add(p);
                    printerAreaMap.put(p.id(), area);
                  });
        }
      }
    }

    if (printersToPrint.isEmpty()) {
      throw new ResourceNotFoundException(
          "No active kitchen printers found for order " + orderId + " in branch " + branchId);
    }

    List<Long> enqueuedJobIds = new ArrayList<>();
    for (PrinterDevice printer : printersToPrint) {
      String area = printerAreaMap.getOrDefault(printer.id(), "COCINA");
      String idempotencyKey =
          "REPRINT-ORDER-"
              + orderId
              + "-AREA-"
              + area
              + "-PRINTER-"
              + printer.id()
              + "-TS-"
              + System.currentTimeMillis();

      PrintJob job =
          new PrintJob(
              null,
              branchId,
              printer.id(),
              orderId,
              PrintDocumentType.KITCHEN_ORDER,
              idempotencyKey,
              PrintJobStatus.PENDING,
              null,
              0,
              null,
              null);

      PrintJob savedJob = printJobRepository.save(job);
      enqueuedJobIds.add(savedJob.id());

      PrintJobMessage message =
          new PrintJobMessage(
              savedJob.id(),
              branchId,
              printer.id(),
              orderId,
              PrintDocumentType.KITCHEN_ORDER,
              printer.ipAddress(),
              printer.port(),
              area,
              orderSummary.tableName(),
              orderSummary.orderType(),
              orderSummary.notes(),
              true);

      String routingKey = "printing.branch." + branchId;
      rabbitTemplate.convertAndSend(PrintingQueueConfig.PRINTING_EXCHANGE, routingKey, message);
    }

    return enqueuedJobIds;
  }

  @Transactional
  public Long reprintCashierReceipt(
      Long branchId, Long orderId, String terminalCode, Long targetPrinterId, String reason) {
    validateReprintCooldown(branchId, orderId, PrintDocumentType.CUSTOMER_RECEIPT);

    CustomerOrderProvider.OrderPrintSummary orderSummary =
        customerOrderProvider
            .getOrderPrintSummary(branchId, orderId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    PrinterDevice printer = null;

    if (targetPrinterId != null) {
      printer =
          printerDeviceRepository
              .findByIdAndBranchId(targetPrinterId, branchId)
              .filter(p -> Boolean.TRUE.equals(p.isActive()))
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Printer " + targetPrinterId + " not found or inactive in branch " + branchId));
    } else if (terminalCode != null && !terminalCode.isBlank()) {
      printer =
          printerDeviceRepository
              .findByBranchIdAndTerminalCodeAndIsActiveTrue(branchId, terminalCode)
              .orElse(null);
    }

    if (printer == null) {
      List<PrinterRoute> routes =
          printerRouteRepository.findAllByBranchIdAndDocumentType(
              branchId, PrintDocumentType.CUSTOMER_RECEIPT);

      for (PrinterRoute route : routes) {
        printer =
            printerDeviceRepository
                .findByIdAndBranchId(route.printerId(), branchId)
                .filter(p -> Boolean.TRUE.equals(p.isActive()))
                .orElse(null);
        if (printer != null) {
          break;
        }
      }
    }

    if (printer == null) {
      throw new ResourceNotFoundException(
          "No active printer found for receipt reprint in branch " + branchId);
    }

    String idempotencyKey =
        "REPRINT-CASHIER-"
            + (terminalCode != null ? terminalCode : "PRINTER-" + printer.id())
            + "-ORDER-"
            + orderId
            + "-TS-"
            + System.currentTimeMillis();

    PrintJob job =
        new PrintJob(
            null,
            branchId,
            printer.id(),
            orderId,
            PrintDocumentType.CUSTOMER_RECEIPT,
            idempotencyKey,
            PrintJobStatus.PENDING,
            null,
            0,
            null,
            null);

    PrintJob savedJob = printJobRepository.save(job);

    PrintJobMessage message =
        new PrintJobMessage(
            savedJob.id(),
            branchId,
            printer.id(),
            orderId,
            PrintDocumentType.CUSTOMER_RECEIPT,
            printer.ipAddress(),
            printer.port(),
            terminalCode,
            orderSummary.tableName(),
            orderSummary.orderType(),
            orderSummary.notes(),
            true);

    String routingKey = "printing.branch." + branchId;
    rabbitTemplate.convertAndSend(PrintingQueueConfig.PRINTING_EXCHANGE, routingKey, message);

    return savedJob.id();
  }

  @Transactional
  public Long retryPrintJob(Long branchId, Long jobId) {
    PrintJob job =
        printJobRepository
            .findByIdAndBranchId(jobId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "PrintJob " + jobId + " not found in branch " + branchId));

    if (!PrintJobStatus.FAILED.equals(job.status())) {
      throw new IllegalStateException(
          "Only FAILED jobs can be retried. Job " + jobId + " status is " + job.status());
    }

    PrinterDevice printer =
        printerDeviceRepository
            .findByIdAndBranchId(job.printerId(), branchId)
            .filter(p -> Boolean.TRUE.equals(p.isActive()))
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Printer " + job.printerId() + " not found or inactive in branch " + branchId));

    CustomerOrderProvider.OrderPrintSummary orderSummary =
        customerOrderProvider.getOrderPrintSummary(branchId, job.orderId()).orElse(null);

    printJobRepository.resetJobForRetry(jobId, branchId);

    PrintJobMessage message =
        new PrintJobMessage(
            job.id(),
            branchId,
            printer.id(),
            job.orderId(),
            job.documentType(),
            printer.ipAddress(),
            printer.port(),
            null,
            orderSummary != null ? orderSummary.tableName() : null,
            orderSummary != null ? orderSummary.orderType() : "DINE_IN",
            orderSummary != null ? orderSummary.notes() : null,
            false);

    String routingKey = "printing.branch." + branchId;
    rabbitTemplate.convertAndSend(PrintingQueueConfig.PRINTING_EXCHANGE, routingKey, message);

    return job.id();
  }

  @Transactional
  public Long requestPreCheckPrint(Long branchId, Long orderId, Long targetPrinterId) {
    CustomerOrderProvider.OrderPreCheckSummary summary =
        customerOrderProvider
            .getOrderPreCheckSummary(branchId, orderId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + orderId + " not found in branch " + branchId));

    boolean isReprint =
        printJobRepository
            .findLatestJob(branchId, orderId, PrintDocumentType.PRE_CHECK.name())
            .isPresent();

    if (isReprint) {
      validateReprintCooldown(branchId, orderId, PrintDocumentType.PRE_CHECK);
    }

    PrinterDevice printer = null;

    if (targetPrinterId != null) {
      printer =
          printerDeviceRepository
              .findByIdAndBranchId(targetPrinterId, branchId)
              .filter(p -> Boolean.TRUE.equals(p.isActive()))
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Printer " + targetPrinterId + " not found or inactive in branch " + branchId));
    }

    if (printer == null) {
      List<PrinterRoute> routes =
          printerRouteRepository.findAllByBranchIdAndDocumentType(
              branchId, PrintDocumentType.PRE_CHECK);
      for (PrinterRoute route : routes) {
        printer =
            printerDeviceRepository
                .findByIdAndBranchId(route.printerId(), branchId)
                .filter(p -> Boolean.TRUE.equals(p.isActive()))
                .orElse(null);
        if (printer != null) {
          break;
        }
      }
    }

    if (printer == null) {
      List<PrinterRoute> routes =
          printerRouteRepository.findAllByBranchIdAndDocumentType(
              branchId, PrintDocumentType.CUSTOMER_RECEIPT);
      for (PrinterRoute route : routes) {
        printer =
            printerDeviceRepository
                .findByIdAndBranchId(route.printerId(), branchId)
                .filter(p -> Boolean.TRUE.equals(p.isActive()))
                .orElse(null);
        if (printer != null) {
          break;
        }
      }
    }

    if (printer == null) {
      printer =
          printerDeviceRepository.findAllByBranchId(branchId).stream()
              .filter(p -> Boolean.TRUE.equals(p.isActive()))
              .findFirst()
              .orElse(null);
    }

    if (printer == null) {
      throw new ResourceNotFoundException(
          "No active printer found for pre-check printing in branch " + branchId);
    }

    String idempotencyKey =
        (isReprint ? "REPRINT-" : "")
            + "PRECHECK-ORDER-"
            + orderId
            + "-PRINTER-"
            + printer.id()
            + "-TS-"
            + System.currentTimeMillis();

    PrintJob job =
        new PrintJob(
            null,
            branchId,
            printer.id(),
            orderId,
            PrintDocumentType.PRE_CHECK,
            idempotencyKey,
            PrintJobStatus.PENDING,
            null,
            0,
            null,
            null);

    PrintJob savedJob = printJobRepository.save(job);

    PrintJobMessage message =
        new PrintJobMessage(
            savedJob.id(),
            branchId,
            printer.id(),
            orderId,
            PrintDocumentType.PRE_CHECK,
            printer.ipAddress(),
            printer.port(),
            null,
            summary.tableName(),
            summary.orderType(),
            null,
            isReprint,
            false);

    String routingKey = "printing.branch." + branchId;
    rabbitTemplate.convertAndSend(PrintingQueueConfig.PRINTING_EXCHANGE, routingKey, message);

    return savedJob.id();
  }
}

