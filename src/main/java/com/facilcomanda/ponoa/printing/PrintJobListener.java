package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.printing.payload.PrintJobDispatchMessage;
import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import com.facilcomanda.ponoa.sales.CustomerOrderProvider;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class PrintJobListener {

  private static final Logger log = LoggerFactory.getLogger(PrintJobListener.class);

  private final PrintJobRepository printJobRepository;
  private final EscPosTicketFormatter ticketFormatter;
  private final EscPosTcpPrinterClient printerClient;
  private final CustomerOrderProvider customerOrderProvider;
  private final PrintRelayPublisher relayPublisher;
  private final PrintingMode printingMode;

  @Autowired
  PrintJobListener(
      PrintJobRepository printJobRepository,
      EscPosTicketFormatter ticketFormatter,
      EscPosTcpPrinterClient printerClient,
      CustomerOrderProvider customerOrderProvider,
      PrintRelayPublisher relayPublisher,
      @Value("${app.printing.mode:HYBRID}") String printingModeStr) {
    this.printJobRepository = printJobRepository;
    this.ticketFormatter = ticketFormatter;
    this.printerClient = printerClient;
    this.customerOrderProvider = customerOrderProvider;
    this.relayPublisher = relayPublisher;
    this.printingMode = parsePrintingMode(printingModeStr);
  }

  PrintJobListener(
      PrintJobRepository printJobRepository,
      EscPosTicketFormatter ticketFormatter,
      EscPosTcpPrinterClient printerClient,
      CustomerOrderProvider customerOrderProvider,
      PrintRelayPublisher relayPublisher) {
    this(
        printJobRepository,
        ticketFormatter,
        printerClient,
        customerOrderProvider,
        relayPublisher,
        "HYBRID");
  }

  private static PrintingMode parsePrintingMode(String mode) {
    if (mode == null) {
      return PrintingMode.HYBRID;
    }
    try {
      return PrintingMode.valueOf(mode.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return PrintingMode.HYBRID;
    }
  }

  @RabbitListener(queues = PrintingQueueConfig.PRINTING_JOBS_QUEUE)
  public void handlePrintJob(PrintJobMessage message) {
    Optional<PrintJob> existing =
        printJobRepository.findByIdAndBranchId(message.jobId(), message.branchId());
    if (existing.isPresent()
        && (PrintJobStatus.COMPLETED.equals(existing.get().status())
            || PrintJobStatus.DISPATCHED.equals(existing.get().status()))) {
      log.info(
          "Print job {} in branch {} is already {}. Skipping duplicate handling.",
          message.jobId(),
          message.branchId(),
          existing.get().status());
      return;
    }

    printJobRepository.updateJobStatus(
        message.jobId(), message.branchId(), PrintJobStatus.PRINTING.name(), null);

    try {
      byte[] bytes;
      if (PrintDocumentType.PRE_CHECK.equals(message.documentType())) {
        CustomerOrderProvider.OrderPreCheckSummary summary =
            customerOrderProvider
                .getOrderPreCheckSummary(message.branchId(), message.orderId())
                .orElse(null);
        bytes =
            ticketFormatter.formatPreCheckTicket(
                message.branchId(), message.orderId(), summary, message.isReprint());
      } else {
        CustomerOrderProvider.OrderPrintSummary orderSummary =
            customerOrderProvider
                .getOrderPrintSummary(message.branchId(), message.orderId())
                .orElse(null);
        if (orderSummary != null
            && orderSummary.items() != null
            && !orderSummary.items().isEmpty()) {
          bytes =
              ticketFormatter.formatTicket(
                  message.branchId(),
                  message.orderId(),
                  message.documentType(),
                  message.targetArea(),
                  message.tableName(),
                  message.orderType(),
                  message.notes(),
                  message.isReprint(),
                  message.isAddition(),
                  orderSummary.items());
        } else {
          bytes =
              ticketFormatter.formatTicket(
                  message.branchId(),
                  message.orderId(),
                  message.documentType(),
                  message.targetArea(),
                  message.tableName(),
                  message.orderType(),
                  message.notes(),
                  message.isReprint(),
                  message.isAddition());
        }
      }

      String payloadBase64 = Base64.getEncoder().encodeToString(bytes);
      PrintJobDispatchMessage dispatchMessage =
          new PrintJobDispatchMessage(
              message.jobId(),
              message.branchId(),
              message.printerId(),
              message.orderId(),
              message.documentType(),
              message.ipAddress(),
              message.port(),
              message.targetArea(),
              message.tableName(),
              message.orderType(),
              payloadBase64,
              Instant.now());

      if (PrintingMode.AGENT.equals(this.printingMode)) {
        relayPublisher.dispatchToBranch(dispatchMessage);
        printJobRepository.updateJobStatus(
            message.jobId(), message.branchId(), PrintJobStatus.DISPATCHED.name(), null);
        return;
      }

      if (PrintingMode.DIRECT.equals(this.printingMode)) {
        printerClient.print(message.ipAddress(), message.port(), bytes);
        printJobRepository.updateJobStatus(
            message.jobId(), message.branchId(), PrintJobStatus.COMPLETED.name(), null);
        return;
      }

      // Default: HYBRID mode
      relayPublisher.dispatchToBranch(dispatchMessage);
      try {
        printerClient.print(message.ipAddress(), message.port(), bytes);
        printJobRepository.updateJobStatus(
            message.jobId(), message.branchId(), PrintJobStatus.COMPLETED.name(), null);
      } catch (PrinterCommunicationException e) {
        log.warn(
            "Direct TCP print failed for job {} at {}:{} (Branch {}). Leaving in DISPATCHED status for local print agent. Reason: {}",
            message.jobId(),
            message.ipAddress(),
            message.port(),
            message.branchId(),
            e.getMessage());
        printJobRepository.updateJobStatus(
            message.jobId(), message.branchId(), PrintJobStatus.DISPATCHED.name(), null);
      }
    } catch (Exception e) {
      printJobRepository.updateJobStatus(
          message.jobId(), message.branchId(), PrintJobStatus.FAILED.name(), e.getMessage());
      throw e; // Rethrow to trigger RabbitMQ retry / DLQ routing
    }
  }
}
