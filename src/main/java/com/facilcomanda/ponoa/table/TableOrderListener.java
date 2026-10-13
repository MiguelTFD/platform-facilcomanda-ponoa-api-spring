package com.facilcomanda.ponoa.table;

import com.facilcomanda.ponoa.sales.OrderPlacedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class TableOrderListener {

  private final TableEventPublisher tableEventPublisher;

  TableOrderListener(TableEventPublisher tableEventPublisher) {
    this.tableEventPublisher = tableEventPublisher;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrderPlaced(OrderPlacedEvent event) {
    if (event.tableId() != null) {
      tableEventPublisher.publishTableStateChanged(
          event.branchId(), event.tableId(), TableState.OCCUPIED, event.orderId());
    }
  }
}
