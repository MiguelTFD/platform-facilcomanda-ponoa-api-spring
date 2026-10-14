package com.facilcomanda.ponoa.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import com.facilcomanda.ponoa.printing.payload.PrinterFailureAlert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrinterDeadLetterListenerTest {

  @Mock private PrintJobRepository printJobRepository;
  @Mock private PrinterAlertPublisher alertPublisher;

  @InjectMocks private PrinterDeadLetterListener deadLetterListener;

  @Test
  @DisplayName("Should process dead letter print job, update DB status and publish WebSocket alert")
  void shouldProcessDeadLetterPrintJob() {
    PrintJobMessage message =
        new PrintJobMessage(
            99L,
            1L,
            10L,
            1001L,
            PrintDocumentType.KITCHEN_ORDER,
            "192.168.1.150",
            9100,
            "COCINA",
            "Mesa 4",
            "DINE_IN",
            "Sin cebolla",
            false,
            false);


    deadLetterListener.handleDeadLetterJob(message);

    verify(printJobRepository)
        .updateJobStatus(
            eq(99L),
            eq(1L),
            eq("DLQ_EXHAUSTED"),
            eq("Agotados todos los reintentos. Falla física o desconexión de red de la impresora."));

    ArgumentCaptor<PrinterFailureAlert> alertCaptor =
        ArgumentCaptor.forClass(PrinterFailureAlert.class);
    verify(alertPublisher).publishPrinterFailure(alertCaptor.capture());

    PrinterFailureAlert alert = alertCaptor.getValue();
    assertThat(alert.branchId()).isEqualTo(1L);
    assertThat(alert.jobId()).isEqualTo(99L);
    assertThat(alert.orderId()).isEqualTo(1001L);
    assertThat(alert.ipAddress()).isEqualTo("192.168.1.150");
    assertThat(alert.port()).isEqualTo(9100);
    assertThat(alert.targetArea()).isEqualTo("COCINA");
    assertThat(alert.actionRequired()).contains("192.168.1.150:9100");
  }
}
