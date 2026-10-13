package com.facilcomanda.ponoa.kitchen;

import com.facilcomanda.ponoa.sales.OrderPlacedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class KitchenOrderListener {

  private final KitchenTicketService kitchenTicketService;

  KitchenOrderListener(KitchenTicketService kitchenTicketService) {
    this.kitchenTicketService = kitchenTicketService;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrderPlaced(OrderPlacedEvent event) {
    kitchenTicketService.createTicketForOrder(event.branchId(), event.orderId(), null);
  }
}
