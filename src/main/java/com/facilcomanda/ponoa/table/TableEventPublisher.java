package com.facilcomanda.ponoa.table;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
class TableEventPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  TableEventPublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void publishTableStateChanged(
      Long branchId, Long tableId, TableState state, Long orderId) {
    TableStateChangedMessage message =
        new TableStateChangedMessage(tableId, branchId, state, orderId);
    messagingTemplate.convertAndSend(
        "/topic/branches/" + branchId + "/tables", message);
  }
}
