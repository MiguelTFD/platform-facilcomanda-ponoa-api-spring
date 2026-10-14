package com.facilcomanda.ponoa.cash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.cash.payload.AddCashMovementRequest;
import com.facilcomanda.ponoa.cash.payload.CashShiftResponse;
import com.facilcomanda.ponoa.cash.payload.CloseCashShiftRequest;
import com.facilcomanda.ponoa.cash.payload.CreateCashRegisterRequest;
import com.facilcomanda.ponoa.cash.payload.OpenCashShiftRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class CashServiceTest {

  @Mock private CashRegisterRepository registerRepository;
  @Mock private CashShiftRepository shiftRepository;
  @Mock private CashMovementRepository movementRepository;

  private CashService cashService;

  @BeforeEach
  void setUp() {
    cashService = new CashService(registerRepository, shiftRepository, movementRepository);
  }

  @Test
  @DisplayName("Should create cash register successfully")
  void shouldCreateCashRegisterSuccessfully() {
    Long branchId = 1L;
    CreateCashRegisterRequest request =
        new CreateCashRegisterRequest("Caja Principal", "Caja al ingreso");

    when(registerRepository.existsByBranchIdAndName(branchId, "Caja Principal")).thenReturn(false);
    when(registerRepository.save(any(CashRegister.class)))
        .thenAnswer(inv -> {
          CashRegister cr = inv.getArgument(0);
          return new CashRegister(10L, cr.branchId(), cr.name(), cr.description(), cr.isActive(), cr.createdAt(), cr.updatedAt());
        });

    Long registerId = cashService.createCashRegister(branchId, request);

    assertThat(registerId).isEqualTo(10L);
    verify(registerRepository).save(any(CashRegister.class));
  }

  @Test
  @DisplayName("Should reject duplicate cash register name in same branch")
  void shouldRejectDuplicateCashRegisterName() {
    Long branchId = 1L;
    CreateCashRegisterRequest request =
        new CreateCashRegisterRequest("Caja 1", null);

    when(registerRepository.existsByBranchIdAndName(branchId, "Caja 1")).thenReturn(true);

    assertThatThrownBy(() -> cashService.createCashRegister(branchId, request))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  @DisplayName("Should open cash shift successfully with opening amount")
  void shouldOpenCashShiftSuccessfully() {
    Long branchId = 1L;
    UUID cashierId = UUID.randomUUID();
    CashRegister register =
        new CashRegister(1L, branchId, "Caja 1", null, true, OffsetDateTime.now(), OffsetDateTime.now());

    when(registerRepository.findByIdAndBranchId(1L, branchId)).thenReturn(Optional.of(register));
    when(shiftRepository.findByCashRegisterIdAndStatus(1L, "OPEN")).thenReturn(Optional.empty());
    when(shiftRepository.findByBranchIdAndCashierUserIdAndStatus(branchId, cashierId, "OPEN"))
        .thenReturn(Optional.empty());

    when(shiftRepository.save(any(CashShift.class)))
        .thenAnswer(inv -> {
          CashShift s = inv.getArgument(0);
          return new CashShift(
              50L,
              s.branchId(),
              s.cashRegisterId(),
              s.cashierUserId(),
              s.cashierName(),
              s.status(),
              s.openingAmount(),
              s.openedAt(),
              s.closedAt(),
              s.closingCashCounted(),
              s.expectedCashAmount(),
              s.cashDifference(),
              s.totalSalesAmount(),
              s.notes(),
              s.createdAt(),
              s.updatedAt());
        });

    OpenCashShiftRequest request =
        new OpenCashShiftRequest(1L, new BigDecimal("200.00"), "Turno mañana");

    Long shiftId = cashService.openCashShift(branchId, cashierId, "Maria Cajera", request);

    assertThat(shiftId).isEqualTo(50L);
    verify(shiftRepository).save(any(CashShift.class));
  }

  @Test
  @DisplayName("Should reject opening shift when cash register already has an active open shift")
  void shouldRejectWhenRegisterAlreadyHasOpenShift() {
    Long branchId = 1L;
    UUID cashierId = UUID.randomUUID();
    CashRegister register =
        new CashRegister(1L, branchId, "Caja 1", null, true, OffsetDateTime.now(), OffsetDateTime.now());

    CashShift activeShift =
        new CashShift(
            99L, branchId, 1L, UUID.randomUUID(), "Otro Cajero", "OPEN",
            new BigDecimal("100.00"), OffsetDateTime.now(), null, null, null, null,
            BigDecimal.ZERO, null, OffsetDateTime.now(), OffsetDateTime.now());

    when(registerRepository.findByIdAndBranchId(1L, branchId)).thenReturn(Optional.of(register));
    when(shiftRepository.findByCashRegisterIdAndStatus(1L, "OPEN")).thenReturn(Optional.of(activeShift));

    OpenCashShiftRequest request = new OpenCashShiftRequest(1L, new BigDecimal("150.00"), null);

    assertThatThrownBy(() -> cashService.openCashShift(branchId, cashierId, "Maria", request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already has an open shift");
  }

  @Test
  @DisplayName("Should reject opening shift when cashier already has an active open shift in branch")
  void shouldRejectWhenCashierAlreadyHasOpenShift() {
    Long branchId = 1L;
    UUID cashierId = UUID.randomUUID();
    CashRegister register =
        new CashRegister(2L, branchId, "Caja 2", null, true, OffsetDateTime.now(), OffsetDateTime.now());

    CashShift cashierActiveShift =
        new CashShift(
            88L, branchId, 1L, cashierId, "Maria", "OPEN",
            new BigDecimal("100.00"), OffsetDateTime.now(), null, null, null, null,
            BigDecimal.ZERO, null, OffsetDateTime.now(), OffsetDateTime.now());

    when(registerRepository.findByIdAndBranchId(2L, branchId)).thenReturn(Optional.of(register));
    when(shiftRepository.findByCashRegisterIdAndStatus(2L, "OPEN")).thenReturn(Optional.empty());
    when(shiftRepository.findByBranchIdAndCashierUserIdAndStatus(branchId, cashierId, "OPEN"))
        .thenReturn(Optional.of(cashierActiveShift));

    OpenCashShiftRequest request = new OpenCashShiftRequest(2L, new BigDecimal("150.00"), null);

    assertThatThrownBy(() -> cashService.openCashShift(branchId, cashierId, "Maria", request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already has an active open shift");
  }

  @Test
  @DisplayName("Should perform blind cash audit and close shift correctly calculating difference (Corte Z)")
  void shouldCloseCashShiftAndCalculateArqueoCorrectly() {
    Long branchId = 1L;
    Long shiftId = 50L;
    UUID cashierId = UUID.randomUUID();

    CashShift openShift =
        new CashShift(
            shiftId,
            branchId,
            1L,
            cashierId,
            "Maria Cajera",
            "OPEN",
            new BigDecimal("200.00"), // opening amount
            OffsetDateTime.now().minusHours(8),
            null,
            null,
            null,
            null,
            BigDecimal.ZERO,
            "Inicio de turno",
            OffsetDateTime.now().minusHours(8),
            OffsetDateTime.now().minusHours(8));

    when(shiftRepository.findByIdAndBranchId(shiftId, branchId)).thenReturn(Optional.of(openShift));

    // Sales payments in this shift
    when(shiftRepository.getPaymentsSummaryByShiftId(shiftId))
        .thenReturn(
            List.of(
                new PaymentSummaryProjection("EFECTIVO", new BigDecimal("300.00")),
                new PaymentSummaryProjection("YAPE", new BigDecimal("150.00")),
                new PaymentSummaryProjection("TARJETA", new BigDecimal("250.00"))));

    when(shiftRepository.sumChangeGivenByShiftId(shiftId)).thenReturn(new BigDecimal("20.00")); // Change given: 20
    // Net cash from sales: 300 - 20 = 280

    // Manual movements:
    when(shiftRepository.sumMovementsByType(shiftId, "INCOME")).thenReturn(new BigDecimal("50.00"));
    when(shiftRepository.sumMovementsByType(shiftId, "EXPENSE")).thenReturn(new BigDecimal("30.00"));

    // Total sales across all payment methods
    when(shiftRepository.sumOrderTotalsByShiftId(shiftId)).thenReturn(new BigDecimal("680.00"));

    when(movementRepository.findAllByCashShiftIdOrderByCreatedAtDesc(shiftId))
        .thenReturn(Collections.emptyList());

    when(shiftRepository.save(any(CashShift.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Expected cash = 200 (opening) + 280 (net sales cash) + 50 (incomes) - 30 (expenses) = 500.00
    // Physical counted cash: 505.00 -> Sobrante: +5.00
    CloseCashShiftRequest closeRequest =
        new CloseCashShiftRequest(new BigDecimal("505.00"), "Cierre conforme con leve sobrante");

    CashShiftResponse response = cashService.closeCashShift(branchId, shiftId, cashierId, closeRequest);

    assertThat(response.status()).isEqualTo("CLOSED");
    assertThat(response.openingAmount()).isEqualByComparingTo("200.00");
    assertThat(response.totalCashCollected()).isEqualByComparingTo("300.00");
    assertThat(response.totalChangeGiven()).isEqualByComparingTo("20.00");
    assertThat(response.totalIncomes()).isEqualByComparingTo("50.00");
    assertThat(response.totalExpenses()).isEqualByComparingTo("30.00");
    assertThat(response.expectedCashAmount()).isEqualByComparingTo("500.00");
    assertThat(response.closingCashCounted()).isEqualByComparingTo("505.00");
    assertThat(response.cashDifference()).isEqualByComparingTo("5.00"); // +5.00 sobrante
    assertThat(response.totalSalesAmount()).isEqualByComparingTo("680.00");

    assertThat(response.paymentsByMethod()).containsEntry("EFECTIVO", new BigDecimal("300.00"));
    assertThat(response.paymentsByMethod()).containsEntry("YAPE", new BigDecimal("150.00"));
    assertThat(response.paymentsByMethod()).containsEntry("TARJETA", new BigDecimal("250.00"));
  }

  @Test
  @DisplayName("Should detect cash deficit (faltante) during shift closure")
  void shouldDetectCashDeficitOnClose() {
    Long branchId = 1L;
    Long shiftId = 51L;
    UUID cashierId = UUID.randomUUID();

    CashShift openShift =
        new CashShift(
            shiftId,
            branchId,
            1L,
            cashierId,
            "Carlos Cajero",
            "OPEN",
            new BigDecimal("100.00"),
            OffsetDateTime.now().minusHours(4),
            null,
            null,
            null,
            null,
            BigDecimal.ZERO,
            null,
            OffsetDateTime.now().minusHours(4),
            OffsetDateTime.now().minusHours(4));

    when(shiftRepository.findByIdAndBranchId(shiftId, branchId)).thenReturn(Optional.of(openShift));
    when(shiftRepository.getPaymentsSummaryByShiftId(shiftId))
        .thenReturn(List.of(new PaymentSummaryProjection("EFECTIVO", new BigDecimal("100.00"))));
    when(shiftRepository.sumChangeGivenByShiftId(shiftId)).thenReturn(BigDecimal.ZERO);
    when(shiftRepository.sumMovementsByType(shiftId, "INCOME")).thenReturn(BigDecimal.ZERO);
    when(shiftRepository.sumMovementsByType(shiftId, "EXPENSE")).thenReturn(BigDecimal.ZERO);
    when(shiftRepository.sumOrderTotalsByShiftId(shiftId)).thenReturn(new BigDecimal("100.00"));

    when(shiftRepository.save(any(CashShift.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Expected = 100 + 100 = 200.00
    // Counted = 180.00 -> Faltante = -20.00
    CloseCashShiftRequest closeRequest =
        new CloseCashShiftRequest(new BigDecimal("180.00"), "Faltante reportado");

    CashShiftResponse response = cashService.closeCashShift(branchId, shiftId, cashierId, closeRequest);

    assertThat(response.expectedCashAmount()).isEqualByComparingTo("200.00");
    assertThat(response.closingCashCounted()).isEqualByComparingTo("180.00");
    assertThat(response.cashDifference()).isEqualByComparingTo("-20.00"); // Faltante
  }

  @Test
  @DisplayName("Should generate Z-Cut report successfully with sales and invoice stats")
  void shouldGenerateZCutReportSuccessfully() {
    Long branchId = 1L;
    Long shiftId = 50L;
    UUID cashierId = UUID.randomUUID();

    CashShift closedShift =
        new CashShift(
            shiftId,
            branchId,
            10L,
            cashierId,
            "Carlos Cajero",
            "CLOSED",
            new BigDecimal("200.00"),
            OffsetDateTime.now().minusHours(8),
            OffsetDateTime.now(),
            new BigDecimal("500.00"),
            new BigDecimal("500.00"),
            BigDecimal.ZERO,
            new BigDecimal("1200.00"),
            "Cierre conforme",
            OffsetDateTime.now().minusHours(8),
            OffsetDateTime.now());

    when(shiftRepository.findByIdAndBranchId(shiftId, branchId)).thenReturn(Optional.of(closedShift));
    when(registerRepository.findByIdAndBranchId(10L, branchId))
        .thenReturn(Optional.of(new CashRegister(10L, branchId, "Caja 1", null, true, null, null)));

    when(shiftRepository.getPaymentsSummaryByShiftId(shiftId))
        .thenReturn(
            List.of(
                new PaymentSummaryProjection("EFECTIVO", new BigDecimal("300.00")),
                new PaymentSummaryProjection("YAPE", new BigDecimal("900.00"))));
    when(shiftRepository.sumChangeGivenByShiftId(shiftId)).thenReturn(BigDecimal.ZERO);
    when(shiftRepository.sumMovementsByType(shiftId, "INCOME")).thenReturn(BigDecimal.ZERO);
    when(shiftRepository.sumMovementsByType(shiftId, "EXPENSE")).thenReturn(BigDecimal.ZERO);
    when(shiftRepository.sumOrderTotalsByShiftId(shiftId)).thenReturn(new BigDecimal("1200.00"));

    when(shiftRepository.countInvoicesByShiftId(shiftId)).thenReturn(15L);
    when(shiftRepository.getInvoiceCountsByShiftId(shiftId))
        .thenReturn(List.of(new InvoiceCountProjection("BOLETA", 12L), new InvoiceCountProjection("FACTURA", 3L)));
    when(movementRepository.findAllByCashShiftIdOrderByCreatedAtDesc(shiftId)).thenReturn(Collections.emptyList());

    com.facilcomanda.ponoa.cash.payload.ZCutReportResponse zCut =
        cashService.getZCutReport(branchId, shiftId);

    assertThat(zCut.shiftId()).isEqualTo(shiftId);
    assertThat(zCut.cashRegisterName()).isEqualTo("Caja 1");
    assertThat(zCut.totalSalesAmount()).isEqualByComparingTo("1200.00");
    assertThat(zCut.totalInvoicesCount()).isEqualTo(15L);
    assertThat(zCut.invoicesByType()).containsEntry("BOLETA", 12L).containsEntry("FACTURA", 3L);
    assertThat(zCut.salesByPaymentMethod()).containsEntry("EFECTIVO", new BigDecimal("300.00"));
    assertThat(zCut.salesByPaymentMethod()).containsEntry("YAPE", new BigDecimal("900.00"));
  }
}

