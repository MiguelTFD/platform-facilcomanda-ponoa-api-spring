package com.facilcomanda.ponoa.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import com.facilcomanda.ponoa.printing.payload.ConfigureRouteRequest;
import com.facilcomanda.ponoa.printing.payload.PrintCashierReceiptRequest;
import com.facilcomanda.ponoa.printing.payload.PrintJobAckRequest;
import com.facilcomanda.ponoa.printing.payload.PrintJobResponse;
import com.facilcomanda.ponoa.printing.payload.PrintPreCheckRequest;
import com.facilcomanda.ponoa.printing.payload.PrinterDeviceResponse;
import com.facilcomanda.ponoa.printing.payload.RegisterPrinterRequest;
import com.facilcomanda.ponoa.printing.payload.ReprintKitchenOrderRequest;
import com.facilcomanda.ponoa.printing.payload.ReprintReceiptRequest;
import com.facilcomanda.ponoa.printing.payload.UpdatePrinterRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class PrinterControllerTest {

  @Mock private PrintJobService printJobService;
  @Mock private BranchAccessValidator branchAccessValidator;
  @Mock private Jwt jwt;

  @InjectMocks private PrinterController printerController;

  @Test
  @DisplayName("Should acknowledge print job and return 200 OK")
  void shouldAcknowledgePrintJob() {
    Long branchId = 3L;
    Long jobId = 42L;
    PrintJobAckRequest request = new PrintJobAckRequest(PrintJobStatus.COMPLETED, null);

    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);

    ResponseEntity<Map<String, Object>> response =
        printerController.acknowledgeJob(jobId, branchId, jwt, request);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("jobId")).isEqualTo(jobId);
    assertThat(response.getBody().get("status")).isEqualTo("COMPLETED");
    assertThat(response.getBody().get("acknowledged")).isEqualTo(true);

    verify(printJobService).acknowledgePrintJob(branchId, jobId, PrintJobStatus.COMPLETED, null);
  }

  @Test
  @DisplayName("Should list printers for branch")
  void shouldListPrinters() {
    Long branchId = 3L;
    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);

    PrinterDevice printer =
        new PrinterDevice(
            1L, branchId, "Ticketera", "192.168.1.50", 9100, "TCP", null, true, null, null);
    when(printJobService.getPrinters(branchId)).thenReturn(List.of(printer));

    ResponseEntity<List<PrinterDeviceResponse>> response =
        printerController.listPrinters(branchId, jwt);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().get(0).name()).isEqualTo("Ticketera");
  }

  @Test
  @DisplayName("Should list print jobs by branch")
  void shouldListPrintJobs() {
    Long branchId = 3L;
    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);

    PrintJob job =
        new PrintJob(
            10L,
            branchId,
            1L,
            100L,
            PrintDocumentType.KITCHEN_ORDER,
            "key",
            PrintJobStatus.DISPATCHED,
            null,
            0,
            null,
            null);
    when(printJobService.getPrintJobsByBranch(branchId, PrintJobStatus.DISPATCHED))
        .thenReturn(List.of(job));

    ResponseEntity<List<PrintJobResponse>> response =
        printerController.listJobs(PrintJobStatus.DISPATCHED, branchId, jwt);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().get(0).id()).isEqualTo(10L);
    assertThat(response.getBody().get(0).status()).isEqualTo(PrintJobStatus.DISPATCHED);
  }

  @Test
  @DisplayName("Should register printer and return 201 Created")
  void shouldRegisterPrinter() {
    Long branchId = 3L;
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/printers");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    RegisterPrinterRequest regRequest =
        new RegisterPrinterRequest("Barra", "192.168.1.60", 9100, "TCP", "BAR-01");
    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);
    when(printJobService.registerPrinter(eq(branchId), any(RegisterPrinterRequest.class)))
        .thenReturn(15L);

    ResponseEntity<Void> response = printerController.registerPrinter(branchId, jwt, regRequest);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().toString()).endsWith("/15");

    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  @DisplayName("Should update printer and return 204 No Content")
  void shouldUpdatePrinter() {
    Long branchId = 3L;
    Long printerId = 15L;
    UpdatePrinterRequest updateReq =
        new UpdatePrinterRequest("Barra Nueva", "192.168.1.61", 9100, "TCP", "BAR-01", true);

    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);

    ResponseEntity<Void> response =
        printerController.updatePrinter(printerId, branchId, jwt, updateReq);

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    verify(printJobService).updatePrinter(branchId, printerId, updateReq);
  }

  @Test
  @DisplayName("Should delete printer and return 204 No Content")
  void shouldDeletePrinter() {
    Long branchId = 3L;
    Long printerId = 15L;

    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);

    ResponseEntity<Void> response = printerController.deletePrinter(printerId, branchId, jwt);

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    verify(printJobService).deletePrinter(branchId, printerId);
  }

  @Test
  @DisplayName("Should configure printer route and return 200 OK")
  void shouldConfigureRoute() {
    Long branchId = 3L;
    Long printerId = 15L;
    ConfigureRouteRequest routeReq =
        new ConfigureRouteRequest(PrintDocumentType.KITCHEN_ORDER, "BARRA");

    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);

    ResponseEntity<Void> response =
        printerController.configureRoute(printerId, branchId, jwt, routeReq);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    verify(printJobService)
        .configureRoute(branchId, printerId, PrintDocumentType.KITCHEN_ORDER, "BARRA");
  }

  @Test
  @DisplayName("Should print cashier receipt and return 202 Accepted")
  void shouldPrintCashierReceipt() {
    Long branchId = 3L;
    PrintCashierReceiptRequest req =
        new PrintCashierReceiptRequest(100L, "CAJA-01", "idem-receipt-1");

    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);

    ResponseEntity<Void> response = printerController.printCashierReceipt(branchId, jwt, req);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verify(printJobService).enqueueCashierReceipt(branchId, 100L, "CAJA-01", "idem-receipt-1");
  }

  @Test
  @DisplayName("Should reprint kitchen order and return 202 Accepted")
  void shouldReprintKitchenOrder() {
    Long branchId = 3L;
    ReprintKitchenOrderRequest req = new ReprintKitchenOrderRequest(100L, "COCINA", 1L, null);

    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);
    when(printJobService.reprintKitchenOrder(branchId, 100L, "COCINA", 1L)).thenReturn(List.of(55L));

    ResponseEntity<Map<String, Object>> response =
        printerController.reprintKitchenOrder(branchId, jwt, req);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("jobIds")).isEqualTo(List.of(55L));
  }

  @Test
  @DisplayName("Should reprint receipt and return 202 Accepted")
  void shouldReprintReceipt() {
    Long branchId = 3L;
    ReprintReceiptRequest req =
        new ReprintReceiptRequest(100L, "CAJA-01", 2L, "Cliente solicitó copia");

    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);
    when(printJobService.reprintCashierReceipt(
            branchId, 100L, "CAJA-01", 2L, "Cliente solicitó copia"))
        .thenReturn(88L);

    ResponseEntity<Map<String, Object>> response =
        printerController.reprintReceipt(branchId, jwt, req);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("jobId")).isEqualTo(88L);
  }

  @Test
  @DisplayName("Should retry failed job and return 202 Accepted")
  void shouldRetryJob() {
    Long branchId = 3L;
    Long jobId = 77L;

    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);
    when(printJobService.retryPrintJob(branchId, jobId)).thenReturn(jobId);

    ResponseEntity<Map<String, Object>> response = printerController.retryJob(jobId, branchId, jwt);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("status")).isEqualTo("RETRYING");
  }

  @Test
  @DisplayName("Should print pre-check and return 202 Accepted")
  void shouldPrintPreCheck() {
    Long branchId = 3L;
    PrintPreCheckRequest req = new PrintPreCheckRequest(100L, 1L);

    when(branchAccessValidator.resolveAndValidateBranchId(jwt, branchId)).thenReturn(branchId);
    when(printJobService.requestPreCheckPrint(branchId, 100L, 1L)).thenReturn(99L);

    ResponseEntity<Map<String, Object>> response = printerController.printPreCheck(branchId, jwt, req);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("jobId")).isEqualTo(99L);
  }
}
