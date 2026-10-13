package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import com.facilcomanda.ponoa.printing.payload.RegisterPrinterRequest;
import java.util.List;
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

  PrintJobService(
      PrinterDeviceRepository printerDeviceRepository,
      PrinterRouteRepository printerRouteRepository,
      PrintJobRepository printJobRepository,
      RabbitTemplate rabbitTemplate) {
    this.printerDeviceRepository = printerDeviceRepository;
    this.printerRouteRepository = printerRouteRepository;
    this.printJobRepository = printJobRepository;
    this.rabbitTemplate = rabbitTemplate;
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
            true,
            null,
            null);

    return printerDeviceRepository.save(device).id();
  }

  @Transactional
  public void configureRoute(Long branchId, Long printerId, PrintDocumentType documentType) {
    printerDeviceRepository
        .findByIdAndBranchId(printerId, branchId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Printer " + printerId + " not found in branch " + branchId));

    if (printerRouteRepository.existsByBranchIdAndPrinterIdAndDocumentType(
        branchId, printerId, documentType)) {
      return; // Already configured
    }

    PrinterRoute route = new PrinterRoute(null, branchId, printerId, documentType, null);
    printerRouteRepository.save(route);
  }

  @Transactional
  public void enqueuePrintJobsForOrder(
      Long branchId, Long orderId, PrintDocumentType documentType) {
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

      PrintJob job =
          new PrintJob(
              null,
              branchId,
              printer.id(),
              orderId,
              documentType,
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
              documentType,
              printer.ipAddress(),
              printer.port());

      String routingKey = "printing.branch." + branchId;
      rabbitTemplate.convertAndSend(
          PrintingQueueConfig.PRINTING_EXCHANGE, routingKey, message);
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
