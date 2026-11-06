package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.printing.payload.PrintJobDispatchMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class PrintRelayPublisher {

  private static final Logger log = LoggerFactory.getLogger(PrintRelayPublisher.class);

  private final SimpMessagingTemplate messagingTemplate;

  public PrintRelayPublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void dispatchToBranch(PrintJobDispatchMessage message) {
    String destination = "/topic/branches/" + message.branchId() + "/print-jobs";
    log.info(
        "Dispatching print job {} (branch {}, printer {}:{}) to WebSocket destination {}",
        message.jobId(),
        message.branchId(),
        message.ipAddress(),
        message.port(),
        destination);
    messagingTemplate.convertAndSend(destination, message);
  }
}
