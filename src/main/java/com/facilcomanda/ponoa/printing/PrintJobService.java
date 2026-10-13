package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.catalog.CatalogPriceProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import com.facilcomanda.ponoa.printing.payload.RegisterPrinterRequest;
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

  private final PrinterDeviceRepository printerDeviceRepository;
  private final PrinterRouteRepository printerRouteRepository;
  private final PrintJobRepository printJobRepository;
  private final RabbitTemplate rabbitTemplate;
  private final CatalogPriceProvider catalogPriceProvider;

  PrintJobService(
      PrinterDeviceRepository printerDeviceRepository,
      PrinterRouteRepository printerRouteRepository,
      PrintJobRepository printJobRepository,
      RabbitTemplate rabbitTemplate,
      CatalogPriceProvider catalogPriceProvider) {
    this.printerDeviceRepository = printerDeviceRepository;
    this.printerRouteRepository = printerRouteRepository;
    this.printJobRepository = printJobRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.catalogPriceProvider = catalogPriceProvider;
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
                  area);

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
}

