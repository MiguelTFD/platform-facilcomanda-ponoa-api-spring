package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import com.facilcomanda.ponoa.sales.CustomerOrderProvider;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class PrintJobListener {

  private final PrintJobRepository printJobRepository;
  private final EscPosTicketFormatter ticketFormatter;
  private final EscPosTcpPrinterClient printerClient;
  private final CustomerOrderProvider customerOrderProvider;

  PrintJobListener(
      PrintJobRepository printJobRepository,
      EscPosTicketFormatter ticketFormatter,
      EscPosTcpPrinterClient printerClient,
      CustomerOrderProvider customerOrderProvider) {
    this.printJobRepository = printJobRepository;
    this.ticketFormatter = ticketFormatter;
    this.printerClient = printerClient;
    this.customerOrderProvider = customerOrderProvider;
  }

  @RabbitListener(queues = PrintingQueueConfig.PRINTING_JOBS_QUEUE)
  public void handlePrintJob(PrintJobMessage message) {
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

      printerClient.print(message.ipAddress(), message.port(), bytes);

      printJobRepository.updateJobStatus(
          message.jobId(), message.branchId(), PrintJobStatus.COMPLETED.name(), null);
    } catch (Exception e) {
      printJobRepository.updateJobStatus(
          message.jobId(), message.branchId(), PrintJobStatus.FAILED.name(), e.getMessage());
      throw e; // Rethrow to trigger RabbitMQ retry / DLQ routing
    }
  }
}
