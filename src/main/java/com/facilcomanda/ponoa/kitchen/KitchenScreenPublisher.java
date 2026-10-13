package com.facilcomanda.ponoa.kitchen;

import com.facilcomanda.ponoa.kitchen.payload.KitchenTicketResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
class KitchenScreenPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  KitchenScreenPublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void publishTicketUpdate(Long branchId, KitchenTicketResponse ticket) {
    String destination = "/topic/branches/" + branchId + "/kitchen";
    messagingTemplate.convertAndSend(destination, ticket);
  }
}
