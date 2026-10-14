package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.sales.OrderItemsAddedEvent;
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
    printJobService.enqueueKitchenPrintJobsForOrder(
        event.branchId(),
        event.orderId(),
        event.skuIds(),
        event.tableName(),
        event.orderType(),
        event.notes());
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrderItemsAdded(OrderItemsAddedEvent event) {
    printJobService.enqueueKitchenPrintJobsForAddition(
        event.branchId(),
        event.orderId(),
        event.addedSkuIds(),
        event.tableName(),
        event.orderType(),
        event.additionNotes());
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPreCheckRequested(com.facilcomanda.ponoa.sales.PreCheckRequestedEvent event) {
    printJobService.requestPreCheckPrint(event.branchId(), event.orderId(), event.targetPrinterId());
  }
}
