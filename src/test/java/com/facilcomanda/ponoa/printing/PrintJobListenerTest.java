package com.facilcomanda.ponoa.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.printing.payload.PrintJobDispatchMessage;
import com.facilcomanda.ponoa.printing.payload.PrintJobMessage;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrintJobListenerTest {

  @Mock private PrintJobRepository printJobRepository;
  @Mock private EscPosTicketFormatter ticketFormatter;
  @Mock private EscPosTcpPrinterClient printerClient;
  @Mock private com.facilcomanda.ponoa.sales.CustomerOrderProvider customerOrderProvider;
  @Mock private PrintRelayPublisher relayPublisher;

  @InjectMocks private PrintJobListener listener;

  @Test
  @DisplayName("HYBRID: Should dispatch to relay and direct socket, marking COMPLETED on socket success")
  void shouldProcessJobSuccessfullyInHybridMode() {
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

    ArgumentCaptor<PrintJobDispatchMessage> captor =
        ArgumentCaptor.forClass(PrintJobDispatchMessage.class);
    verify(relayPublisher).dispatchToBranch(captor.capture());
    PrintJobDispatchMessage dispatched = captor.getValue();
    assertThat(dispatched.jobId()).isEqualTo(1L);
    assertThat(dispatched.branchId()).isEqualTo(10L);
    assertThat(dispatched.payloadBase64()).isNotEmpty();

    verify(printerClient).print("192.168.1.50", 9100, bytes);
    verify(printJobRepository)
        .updateJobStatus(1L, 10L, PrintJobStatus.COMPLETED.name(), null);
  }

  @Test
  @DisplayName("HYBRID: Should fallback to DISPATCHED without throwing when direct socket connection fails")
  void shouldFallbackToDispatchedOnCommunicationFailureInHybridMode() {
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

    listener.handlePrintJob(message);

    verify(relayPublisher).dispatchToBranch(any(PrintJobDispatchMessage.class));
    verify(printJobRepository)
        .updateJobStatus(1L, 10L, PrintJobStatus.DISPATCHED.name(), null);
    verify(printJobRepository, never())
        .updateJobStatus(eq(1L), eq(10L), eq(PrintJobStatus.FAILED.name()), any());
  }

  @Test
  @DisplayName("AGENT: Should dispatch to relay and immediately mark DISPATCHED without touching socket")
  void shouldDispatchInAgentMode() {
    PrintJobListener agentListener =
        new PrintJobListener(
            printJobRepository,
            ticketFormatter,
            printerClient,
            customerOrderProvider,
            relayPublisher,
            "AGENT");

    PrintJobMessage message =
        new PrintJobMessage(
            1L, 10L, 100L, 500L, PrintDocumentType.KITCHEN_ORDER, "192.168.1.50", 9100);

    byte[] bytes = new byte[] {0x1B, 0x40};
    when(ticketFormatter.formatTicket(
            10L, 500L, PrintDocumentType.KITCHEN_ORDER, null, null, "DINE_IN", null, false, false))
        .thenReturn(bytes);

    agentListener.handlePrintJob(message);

    verify(relayPublisher).dispatchToBranch(any(PrintJobDispatchMessage.class));
    verify(printJobRepository)
        .updateJobStatus(1L, 10L, PrintJobStatus.DISPATCHED.name(), null);
    org.mockito.Mockito.verifyNoInteractions(printerClient);
  }

  @Test
  @DisplayName("DIRECT: Should mark job as FAILED and rethrow when printer socket fails")
  void shouldFailAndRethrowInDirectMode() {
    PrintJobListener directListener =
        new PrintJobListener(
            printJobRepository,
            ticketFormatter,
            printerClient,
            customerOrderProvider,
            relayPublisher,
            "DIRECT");

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

    assertThatThrownBy(() -> directListener.handlePrintJob(message))
        .isInstanceOf(PrinterCommunicationException.class)
        .hasMessageContaining("Connection refused");

    verify(printJobRepository)
        .updateJobStatus(eq(1L), eq(10L), eq(PrintJobStatus.FAILED.name()), any());
    verify(relayPublisher, never()).dispatchToBranch(any());
  }

  @Test
  @DisplayName("Should skip printing if job is already marked as COMPLETED or DISPATCHED")
  void shouldSkipIfJobAlreadyCompletedOrDispatched() {
    PrintJobMessage message =
        new PrintJobMessage(
            1L, 10L, 100L, 500L, PrintDocumentType.KITCHEN_ORDER, "192.168.1.50", 9100);

    PrintJob completedJob =
        new PrintJob(
            1L, 10L, 100L, 500L, PrintDocumentType.KITCHEN_ORDER, "idem-1", PrintJobStatus.COMPLETED,
            null, 0, null, null);

    when(printJobRepository.findByIdAndBranchId(1L, 10L)).thenReturn(Optional.of(completedJob));

    listener.handlePrintJob(message);

    org.mockito.Mockito.verifyNoInteractions(printerClient);
    org.mockito.Mockito.verifyNoInteractions(ticketFormatter);
    org.mockito.Mockito.verifyNoInteractions(relayPublisher);
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
        .thenReturn(Optional.of(summary));

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
