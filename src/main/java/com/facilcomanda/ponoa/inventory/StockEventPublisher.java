package com.facilcomanda.ponoa.inventory;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
class StockEventPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  StockEventPublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void publishStockUpdate(
      Long branchId, Long skuId, Integer remainingStock, boolean isAvailable) {
    String destination = "/topic/branches/" + branchId + "/stock";
    StockUpdateMessage message =
        new StockUpdateMessage(branchId, skuId, remainingStock, isAvailable);
    messagingTemplate.convertAndSend(destination, message);
  }
}
