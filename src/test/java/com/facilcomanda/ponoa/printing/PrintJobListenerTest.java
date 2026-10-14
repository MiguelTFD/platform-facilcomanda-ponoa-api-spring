package com.facilcomanda.ponoa.printing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrintJobListenerTest {

  @Mock private PrintJobRepository printJobRepository;
  @Mock private EscPosTicketFormatter ticketFormatter;
  @Mock private EscPosTcpPrinterClient printerClient;
  @Mock private com.facilcomanda.ponoa.sales.CustomerOrderProvider customerOrderProvider;

  @InjectMocks private PrintJobListener listener;

  @Test
  @DisplayName("Should process print job successfully and transition status to COMPLETED")
  void shouldProcessJobSuccessfully() {
    PrintJobMessage message =
        new PrintJobMessage(
            1L, 10L, 100L, 500L, PrintDocumentType.KITCHEN_ORDER, "192.168.1.50", 9100);

    byte[] bytes = new byte[] {0x1B, 0x40};
    when(ticketFormatter.formatTicket(
            10L, 500L, PrintDocumentType.KITCHEN_ORDER, null, null, "DINE_IN", null, false, false))
        .thenReturn(bytes);

    listener.handlePrintJob(message);

    verify(printJobRepository)
        .updateJobStatus(1L, 10L, PrintJobStatus.PRINTING.name(), null);
    verify(printerClient).print("192.168.1.50", 9100, bytes);
    verify(printJobRepository)
        .updateJobStatus(1L, 10L, PrintJobStatus.COMPLETED.name(), null);
  }

  @Test
  @DisplayName("Should pass targetArea to ticketFormatter when present")
  void shouldPassTargetAreaToFormatter() {
    PrintJobMessage message =
        new PrintJobMessage(
            2L, 10L, 101L, 500L, PrintDocumentType.KITCHEN_ORDER, "192.168.1.51", 9100, "PARRILLA");

    byte[] bytes = new byte[] {0x1B, 0x40};
    when(ticketFormatter.formatTicket(
            10L, 500L, PrintDocumentType.KITCHEN_ORDER, "PARRILLA", null, "DINE_IN", null, false, false))
        .thenReturn(bytes);

    listener.handlePrintJob(message);

    verify(ticketFormatter)
        .formatTicket(
            10L, 500L, PrintDocumentType.KITCHEN_ORDER, "PARRILLA", null, "DINE_IN", null, false, false);
    verify(printerClient).print("192.168.1.51", 9100, bytes);
  }

  @Test
  @DisplayName("Should mark job as FAILED and rethrow exception when printer connection fails")
  void shouldMarkJobFailedOnException() {
    PrintJobMessage message =
        new PrintJobMessage(
            1L, 10L, 100L, 500L, PrintDocumentType.KITCHEN_ORDER, "192.168.1.50", 9100);

    byte[] bytes = new byte[] {0x1B, 0x40};
    when(ticketFormatter.formatTicket(
            10L, 500L, PrintDocumentType.KITCHEN_ORDER, null, null, "DINE_IN", null, false, false))
        .thenReturn(bytes);

    doThrow(new PrinterCommunicationException("Connection refused", null))
        .when(printerClient)
        .print("192.168.1.50", 9100, bytes);

    assertThatThrownBy(() -> listener.handlePrintJob(message))
        .isInstanceOf(PrinterCommunicationException.class)
        .hasMessageContaining("Connection refused");

    verify(printJobRepository)
        .updateJobStatus(eq(1L), eq(10L), eq(PrintJobStatus.FAILED.name()), any());
  }

  @Test
  @DisplayName("Should process PRE_CHECK print job using formatPreCheckTicket")
  void shouldProcessPreCheckJobSuccessfully() {
    PrintJobMessage message =
        new PrintJobMessage(
            5L, 10L, 100L, 500L, PrintDocumentType.PRE_CHECK, "192.168.1.50", 9100);

    com.facilcomanda.ponoa.sales.CustomerOrderProvider.OrderPreCheckSummary summary =
        new com.facilcomanda.ponoa.sales.CustomerOrderProvider.OrderPreCheckSummary(
            500L,
            10L,
            1L,
            "Mesa 1",
            "DINE_IN",
            "user@test.com",
            new java.math.BigDecimal("50.00"),
            new java.math.BigDecimal("42.37"),
            new java.math.BigDecimal("7.63"),
            new java.math.BigDecimal("5.00"),
            java.util.List.of());

    when(customerOrderProvider.getOrderPreCheckSummary(10L, 500L))
        .thenReturn(java.util.Optional.of(summary));

    byte[] bytes = new byte[] {0x1B, 0x40};
    when(ticketFormatter.formatPreCheckTicket(10L, 500L, summary, false))
        .thenReturn(bytes);

    listener.handlePrintJob(message);

    verify(printJobRepository)
        .updateJobStatus(5L, 10L, PrintJobStatus.PRINTING.name(), null);
    verify(ticketFormatter).formatPreCheckTicket(10L, 500L, summary, false);
    verify(printerClient).print("192.168.1.50", 9100, bytes);
    verify(printJobRepository)
        .updateJobStatus(5L, 10L, PrintJobStatus.COMPLETED.name(), null);
  }
}
