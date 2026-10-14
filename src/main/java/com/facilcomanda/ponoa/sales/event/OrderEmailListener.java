package com.facilcomanda.ponoa.sales.event;

import com.facilcomanda.ponoa.sales.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class OrderEmailListener {

  private static final Logger log = LoggerFactory.getLogger(OrderEmailListener.class);

  private final JavaMailSender mailSender;

  OrderEmailListener(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void handleOrderPlacedEvent(OrderPlacedEvent event) {

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom("no-reply@facilcomanda.com");
    message.setTo(event.customerEmail(), "pedidos@facilcomanda.com");
    message.setSubject("Confirmacion de Orden #" + event.orderId() + " - FacilComanda Ponoa");
    message.setText(
        "Tu orden ha sido registrada exitosamente por un total de S/ "
            + event.totalAmount()
            + ".\nEstamos procesando tu solicitud.");
    try {
      mailSender.send(message);
    } catch (Exception e) {
      log.error("Failed to send order placement email for order {}: {}", event.orderId(), e.getMessage(), e);
    }
  }
}
