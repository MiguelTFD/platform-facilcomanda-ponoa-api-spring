package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class PrintJobListener {

  private final PrintJobRepository printJobRepository;
  private final EscPosTicketFormatter ticketFormatter;
  private final EscPosTcpPrinterClient printerClient;

  PrintJobListener(
      PrintJobRepository printJobRepository,
      EscPosTicketFormatter ticketFormatter,
      EscPosTcpPrinterClient printerClient) {
    this.printJobRepository = printJobRepository;
    this.ticketFormatter = ticketFormatter;
    this.printerClient = printerClient;
  }

  @RabbitListener(queues = PrintingQueueConfig.PRINTING_JOBS_QUEUE)
  public void handlePrintJob(PrintJobMessage message) {
    printJobRepository.updateJobStatus(
        message.jobId(), message.branchId(), PrintJobStatus.PRINTING.name(), null);

    try {
      byte[] bytes =
          ticketFormatter.formatTicket(
              message.branchId(), message.orderId(), message.documentType());

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
