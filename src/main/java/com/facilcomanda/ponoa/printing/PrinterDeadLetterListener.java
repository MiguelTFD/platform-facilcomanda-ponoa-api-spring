package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import com.facilcomanda.ponoa.printing.payload.PrinterFailureAlert;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PrinterDeadLetterListener {

  private static final Logger log = LoggerFactory.getLogger(PrinterDeadLetterListener.class);

  private final PrintJobRepository printJobRepository;
  private final PrinterAlertPublisher alertPublisher;

  public PrinterDeadLetterListener(
      PrintJobRepository printJobRepository, PrinterAlertPublisher alertPublisher) {
    this.printJobRepository = printJobRepository;
    this.alertPublisher = alertPublisher;
  }

  @RabbitListener(queues = PrintingQueueConfig.PRINTING_DLQ)
  public void handleDeadLetterJob(PrintJobMessage message) {
    log.error(
        "Hardware failure alert: Print job {} for order {} exhausted retries on printer {}:{} (Branch {})",
        message.jobId(),
        message.orderId(),
        message.ipAddress(),
        message.port(),
        message.branchId());

    printJobRepository.updateJobStatus(
        message.jobId(),
        message.branchId(),
        "DLQ_EXHAUSTED",
        "Agotados todos los reintentos. Falla física o desconexión de red de la impresora.");

    PrinterFailureAlert alert =
        new PrinterFailureAlert(
            message.branchId(),
            message.jobId(),
            message.orderId(),
            message.documentType() != null ? message.documentType().name() : "UNKNOWN",
            message.ipAddress(),
            message.port(),
            message.targetArea(),
            "No se pudo imprimir tras múltiples reintentos.",
            "Verifique si la impresora tiene papel, está encendida o revise la conexión de red (IP: "
                + message.ipAddress()
                + ":"
                + message.port()
                + ")",
            Instant.now());

    alertPublisher.publishPrinterFailure(alert);
  }
}
