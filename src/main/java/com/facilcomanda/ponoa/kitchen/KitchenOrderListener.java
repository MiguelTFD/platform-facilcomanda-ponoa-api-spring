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
    StringBuilder notesBuilder = new StringBuilder();
    if (event.tableName() != null && !event.tableName().isBlank()) {
      notesBuilder.append("Mesa: ").append(event.tableName());
    }
    if (event.orderType() != null && !event.orderType().isBlank()) {
      if (notesBuilder.length() > 0) notesBuilder.append(" | ");
      notesBuilder.append("Tipo: ").append(event.orderType());
    }
    if (event.notes() != null && !event.notes().isBlank()) {
      if (notesBuilder.length() > 0) notesBuilder.append(" | ");
      notesBuilder.append("Notas: ").append(event.notes());
    }

    String ticketNotes = notesBuilder.length() > 0 ? notesBuilder.toString() : null;
    kitchenTicketService.createTicketForOrder(event.branchId(), event.orderId(), ticketNotes);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrderItemsAdded(com.facilcomanda.ponoa.sales.OrderItemsAddedEvent event) {
    kitchenTicketService.appendAdditionToTicket(
        event.branchId(), event.orderId(), event.tableName(), event.additionNotes());
  }
}
