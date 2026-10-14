package com.facilcomanda.ponoa.cash;

import com.facilcomanda.ponoa.cash.payload.AddCashMovementRequest;
import com.facilcomanda.ponoa.cash.payload.CashMovementResponse;
import com.facilcomanda.ponoa.cash.payload.CashRegisterResponse;
import com.facilcomanda.ponoa.cash.payload.CashShiftResponse;
import com.facilcomanda.ponoa.cash.payload.CloseCashShiftRequest;
import com.facilcomanda.ponoa.cash.payload.CreateCashRegisterRequest;
import com.facilcomanda.ponoa.cash.payload.OpenCashShiftRequest;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.branch.BranchProvider;
import com.facilcomanda.ponoa.cash.payload.ZCutReportResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashService implements CashShiftProvider {

  private final CashRegisterRepository registerRepository;
  private final CashShiftRepository shiftRepository;
  private final CashMovementRepository movementRepository;
  private final BranchProvider branchProvider;

  public CashService(
      CashRegisterRepository registerRepository,
      CashShiftRepository shiftRepository,
      CashMovementRepository movementRepository,
      BranchProvider branchProvider) {
    this.registerRepository = registerRepository;
    this.shiftRepository = shiftRepository;
    this.movementRepository = movementRepository;
    this.branchProvider = branchProvider;
  }

  public CashService(
      CashRegisterRepository registerRepository,
      CashShiftRepository shiftRepository,
      CashMovementRepository movementRepository) {
    this(registerRepository, shiftRepository, movementRepository, null);
  }


  @Override
  @Transactional(readOnly = true)
  public Optional<Long> findActiveShiftIdForCashier(Long branchId, UUID cashierUserId) {
    return shiftRepository
        .findByBranchIdAndCashierUserIdAndStatus(branchId, cashierUserId, CashShiftStatus.OPEN.name())
        .map(CashShift::id);
  }

  @Transactional
  public Long createCashRegister(Long branchId, CreateCashRegisterRequest request) {
    if (registerRepository.existsByBranchIdAndName(branchId, request.name())) {
      throw new DuplicateKeyException(
          "A cash register with name '" + request.name() + "' already exists in branch " + branchId);
    }

    OffsetDateTime now = OffsetDateTime.now();
    CashRegister register =
        new CashRegister(null, branchId, request.name(), request.description(), true, now, now);

    return registerRepository.save(register).id();
  }

  @Transactional(readOnly = true)
  public List<CashRegisterResponse> listCashRegisters(Long branchId) {
    return registerRepository.findAllByBranchIdAndIsActiveTrue(branchId).stream()
        .map(
            r ->
                new CashRegisterResponse(
                    r.id(), r.branchId(), r.name(), r.description(), r.isActive(), r.createdAt()))
        .toList();
  }

  @Transactional
  public Long openCashShift(
      Long branchId,
      UUID cashierUserId,
      String cashierName,
      OpenCashShiftRequest request) {

    CashRegister register =
        registerRepository
            .findByIdAndBranchId(request.cashRegisterId(), branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Cash register " + request.cashRegisterId() + " not found in branch " + branchId));

    if (!Boolean.TRUE.equals(register.isActive())) {
      throw new IllegalStateException("Cash register " + register.name() + " is not active.");
    }

    // Verify register is not currently open
    Optional<CashShift> openRegisterShift =
        shiftRepository.findByCashRegisterIdAndStatus(register.id(), CashShiftStatus.OPEN.name());
    if (openRegisterShift.isPresent()) {
      throw new IllegalStateException(
          "Cash register '" + register.name() + "' already has an open shift (Shift ID: "
              + openRegisterShift.get().id() + ")");
    }

    // Verify cashier does not already have an open shift in this branch
    Optional<CashShift> cashierOpenShift =
        shiftRepository.findByBranchIdAndCashierUserIdAndStatus(
            branchId, cashierUserId, CashShiftStatus.OPEN.name());
    if (cashierOpenShift.isPresent()) {
      throw new IllegalStateException(
          "Cashier already has an active open shift in this branch (Shift ID: "
              + cashierOpenShift.get().id() + ")");
    }

    OffsetDateTime now = OffsetDateTime.now();
    CashShift shift =
        new CashShift(
            null,
            branchId,
            register.id(),
            cashierUserId,
            cashierName,
            CashShiftStatus.OPEN.name(),
            request.openingAmount(),
            now,
            null,
            null,
            null,
            null,
            BigDecimal.ZERO,
            request.notes(),
            now,
            now);

    return shiftRepository.save(shift).id();
  }

  @Transactional
  public Long addCashMovement(
      Long branchId,
      Long shiftId,
      UUID userId,
      AddCashMovementRequest request) {

    CashShift shift =
        shiftRepository
            .findByIdAndBranchId(shiftId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Cash shift " + shiftId + " not found in branch " + branchId));

    if (!CashShiftStatus.OPEN.name().equalsIgnoreCase(shift.status())) {
      throw new IllegalStateException("Cannot add cash movements to a closed shift.");
    }

    OffsetDateTime now = OffsetDateTime.now();
    CashMovement movement =
        new CashMovement(
            null,
            shift.id(),
            request.type().name(),
            request.amount(),
            request.reason(),
            userId,
            now);

    return movementRepository.save(movement).id();
  }

  @Transactional(readOnly = true)
  public CashShiftResponse getCurrentShift(Long branchId, UUID cashierUserId) {
    CashShift shift =
        shiftRepository
            .findByBranchIdAndCashierUserIdAndStatus(
                branchId, cashierUserId, CashShiftStatus.OPEN.name())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No active open shift found for cashier in branch " + branchId));

    return buildShiftResponse(shift);
  }

  @Transactional(readOnly = true)
  public CashShiftResponse getShiftById(Long branchId, Long shiftId) {
    CashShift shift =
        shiftRepository
            .findByIdAndBranchId(shiftId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Cash shift " + shiftId + " not found in branch " + branchId));

    return buildShiftResponse(shift);
  }

  @Transactional(readOnly = true)
  public List<CashShiftResponse> listShifts(Long branchId) {
    return shiftRepository.findAllByBranchIdOrderByOpenedAtDesc(branchId).stream()
        .map(this::buildShiftResponse)
        .toList();
  }

  @Transactional
  public CashShiftResponse closeCashShift(
      Long branchId,
      Long shiftId,
      UUID cashierUserId,
      CloseCashShiftRequest request) {

    CashShift shift =
        shiftRepository
            .findByIdAndBranchId(shiftId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Cash shift " + shiftId + " not found in branch " + branchId));

    if (!CashShiftStatus.OPEN.name().equalsIgnoreCase(shift.status())) {
      throw new IllegalStateException("Shift " + shiftId + " is already CLOSED.");
    }

    // 1. Calculate Payments Summary
    Map<String, BigDecimal> paymentsByMethod =
        shiftRepository.getPaymentsSummaryByShiftId(shiftId).stream()
            .collect(
                Collectors.toMap(
                    PaymentSummaryProjection::method,
                    PaymentSummaryProjection::total,
                    BigDecimal::add));

    BigDecimal totalCashCollected = paymentsByMethod.getOrDefault("EFECTIVO", BigDecimal.ZERO);
    BigDecimal totalChangeGiven = shiftRepository.sumChangeGivenByShiftId(shiftId);
    BigDecimal netCashFromSales = totalCashCollected.subtract(totalChangeGiven);

    // 2. Calculate Manual Movements
    BigDecimal totalIncomes = shiftRepository.sumMovementsByType(shiftId, CashMovementType.INCOME.name());
    BigDecimal totalExpenses = shiftRepository.sumMovementsByType(shiftId, CashMovementType.EXPENSE.name());

    // 3. Expected Cash = opening + netCashFromSales + incomes - expenses
    BigDecimal expectedCash =
        shift.openingAmount()
            .add(netCashFromSales)
            .add(totalIncomes)
            .subtract(totalExpenses);

    BigDecimal countedCash = request.countedCash();
    BigDecimal cashDifference = countedCash.subtract(expectedCash);

    // 4. Total Sales (all payment methods combined)
    BigDecimal totalSalesAmount = shiftRepository.sumOrderTotalsByShiftId(shiftId);

    OffsetDateTime now = OffsetDateTime.now();
    CashShift closedShift =
        new CashShift(
            shift.id(),
            shift.branchId(),
            shift.cashRegisterId(),
            shift.cashierUserId(),
            shift.cashierName(),
            CashShiftStatus.CLOSED.name(),
            shift.openingAmount(),
            shift.openedAt(),
            now,
            countedCash,
            expectedCash,
            cashDifference,
            totalSalesAmount,
            request.notes() != null ? request.notes() : shift.notes(),
            shift.createdAt(),
            now);

    CashShift saved = shiftRepository.save(closedShift);
    return buildShiftResponse(saved);
  }

  private CashShiftResponse buildShiftResponse(CashShift shift) {
    Map<String, BigDecimal> paymentsByMethod =
        shiftRepository.getPaymentsSummaryByShiftId(shift.id()).stream()
            .collect(
                Collectors.toMap(
                    PaymentSummaryProjection::method,
                    PaymentSummaryProjection::total,
                    BigDecimal::add));

    BigDecimal totalCashCollected = paymentsByMethod.getOrDefault("EFECTIVO", BigDecimal.ZERO);
    BigDecimal totalChangeGiven = shiftRepository.sumChangeGivenByShiftId(shift.id());
    BigDecimal netCashFromSales = totalCashCollected.subtract(totalChangeGiven);

    BigDecimal totalIncomes =
        shiftRepository.sumMovementsByType(shift.id(), CashMovementType.INCOME.name());
    BigDecimal totalExpenses =
        shiftRepository.sumMovementsByType(shift.id(), CashMovementType.EXPENSE.name());

    BigDecimal expectedCash =
        shift.expectedCashAmount() != null
            ? shift.expectedCashAmount()
            : shift.openingAmount().add(netCashFromSales).add(totalIncomes).subtract(totalExpenses);

    BigDecimal totalSales =
        shift.totalSalesAmount() != null && shift.totalSalesAmount().compareTo(BigDecimal.ZERO) > 0
            ? shift.totalSalesAmount()
            : shiftRepository.sumOrderTotalsByShiftId(shift.id());

    List<CashMovementResponse> movements =
        movementRepository.findAllByCashShiftIdOrderByCreatedAtDesc(shift.id()).stream()
            .map(
                m ->
                    new CashMovementResponse(
                        m.id(),
                        m.cashShiftId(),
                        m.type(),
                        m.amount(),
                        m.reason(),
                        m.performedByUserId(),
                        m.createdAt()))
            .toList();

    return new CashShiftResponse(
        shift.id(),
        shift.branchId(),
        shift.cashRegisterId(),
        shift.cashierUserId(),
        shift.cashierName(),
        shift.status(),
        shift.openingAmount(),
        shift.openedAt(),
        shift.closedAt(),
        shift.closingCashCounted(),
        expectedCash,
        shift.cashDifference(),
        totalSales,
        totalCashCollected,
        totalChangeGiven,
        totalIncomes,
        totalExpenses,
        paymentsByMethod,
        shift.notes(),
        movements);
  }

  @Transactional(readOnly = true)
  public ZCutReportResponse getZCutReport(Long branchId, Long shiftId) {
    CashShift shift =
        shiftRepository
            .findByIdAndBranchId(shiftId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Cash shift " + shiftId + " not found in branch " + branchId));

    String branchName =
        branchProvider != null
            ? branchProvider.findBranchName(branchId).orElse("Sucursal #" + branchId)
            : "Sucursal #" + branchId;

    String registerName =
        registerRepository
            .findByIdAndBranchId(shift.cashRegisterId(), branchId)
            .map(CashRegister::name)
            .orElse("Caja #" + shift.cashRegisterId());

    Map<String, BigDecimal> paymentsByMethod =
        shiftRepository.getPaymentsSummaryByShiftId(shiftId).stream()
            .collect(
                Collectors.toMap(
                    PaymentSummaryProjection::method,
                    PaymentSummaryProjection::total,
                    BigDecimal::add));

    BigDecimal totalCashCollected = paymentsByMethod.getOrDefault("EFECTIVO", BigDecimal.ZERO);
    BigDecimal totalChangeGiven = shiftRepository.sumChangeGivenByShiftId(shiftId);
    BigDecimal netCashFromSales = totalCashCollected.subtract(totalChangeGiven);

    BigDecimal totalIncomes =
        shiftRepository.sumMovementsByType(shiftId, CashMovementType.INCOME.name());
    BigDecimal totalExpenses =
        shiftRepository.sumMovementsByType(shiftId, CashMovementType.EXPENSE.name());

    BigDecimal expectedCash =
        shift.openingAmount()
            .add(netCashFromSales)
            .add(totalIncomes)
            .subtract(totalExpenses);

    BigDecimal totalSalesAmount = shiftRepository.sumOrderTotalsByShiftId(shiftId);

    long totalInvoices = shiftRepository.countInvoicesByShiftId(shiftId);
    Map<String, Long> invoicesByType =
        shiftRepository.getInvoiceCountsByShiftId(shiftId).stream()
            .collect(
                Collectors.toMap(
                    InvoiceCountProjection::type,
                    InvoiceCountProjection::count,
                    Long::sum));

    List<CashMovementResponse> movements =
        movementRepository.findAllByCashShiftIdOrderByCreatedAtDesc(shiftId).stream()
            .map(
                m ->
                    new CashMovementResponse(
                        m.id(),
                        m.cashShiftId(),
                        m.type(),
                        m.amount(),
                        m.reason(),
                        m.performedByUserId(),
                        m.createdAt()))
            .toList();

    return new ZCutReportResponse(
        shift.id(),
        shift.branchId(),
        branchName,
        shift.cashRegisterId(),
        registerName,
        shift.cashierUserId(),
        shift.cashierName(),
        shift.status(),
        shift.openedAt(),
        shift.closedAt(),
        shift.openingAmount(),
        totalSalesAmount,
        paymentsByMethod,
        totalIncomes,
        totalExpenses,
        totalCashCollected,
        totalChangeGiven,
        netCashFromSales,
        expectedCash,
        shift.closingCashCounted(),
        shift.cashDifference(),
        totalInvoices,
        invoicesByType,
        shift.notes(),
        movements);
  }
}

