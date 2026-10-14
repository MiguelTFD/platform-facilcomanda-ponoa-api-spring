package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.printing.payload.PrinterFailureAlert;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class PrinterAlertPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public PrinterAlertPublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void publishPrinterFailure(PrinterFailureAlert alert) {
    String destination = "/topic/branches/" + alert.branchId() + "/printer-alerts";
    messagingTemplate.convertAndSend(destination, alert);
  }
}
