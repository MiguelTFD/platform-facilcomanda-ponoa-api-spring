package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.sales.OrderPlacedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class PrintOrderListener {

  private final PrintJobService printJobService;

  PrintOrderListener(PrintJobService printJobService) {
    this.printJobService = printJobService;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrderPlaced(OrderPlacedEvent event) {
    printJobService.enqueuePrintJobsForOrder(
        event.branchId(), event.orderId(), PrintDocumentType.KITCHEN_ORDER);
  }
}
