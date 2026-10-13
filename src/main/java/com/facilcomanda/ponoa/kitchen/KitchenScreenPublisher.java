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

  public void publishOrderStatusNotification(
      Long branchId, Long orderId, KitchenTicketStatus status, String notes) {
    String destination = "/topic/branches/" + branchId + "/orders";
    OrderStatusNotification message =
        new OrderStatusNotification(orderId, branchId, status, notes);
    messagingTemplate.convertAndSend(destination, message);
  }
}
