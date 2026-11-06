package com.facilcomanda.ponoa.billing;

import com.facilcomanda.ponoa.billing.payload.InvoicePaymentPayload;
import com.facilcomanda.ponoa.billing.payload.InvoiceResponse;
import com.facilcomanda.ponoa.billing.payload.PayOrderRequest;
import com.facilcomanda.ponoa.cash.CashShiftProvider;
import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.sales.CustomerOrderProvider;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

  private final CustomerRepository customerRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceSeriesRepository invoiceSeriesRepository;
  private final CustomerOrderProvider customerOrderProvider;
  private final CashShiftProvider cashShiftProvider;

  @Autowired
  public InvoiceService(
      CustomerRepository customerRepository,
      InvoiceRepository invoiceRepository,
      InvoiceSeriesRepository invoiceSeriesRepository,
      CustomerOrderProvider customerOrderProvider,
      Optional<CashShiftProvider> cashShiftProvider) {
    this.customerRepository = customerRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceSeriesRepository = invoiceSeriesRepository;
    this.customerOrderProvider = customerOrderProvider;
    this.cashShiftProvider = cashShiftProvider.orElse(null);
  }

  public InvoiceService(
      CustomerRepository customerRepository,
      InvoiceRepository invoiceRepository,
      InvoiceSeriesRepository invoiceSeriesRepository,
      CustomerOrderProvider customerOrderProvider) {
    this(customerRepository, invoiceRepository, invoiceSeriesRepository, customerOrderProvider, Optional.empty());
  }

  @Transactional
  public InvoiceResponse payOrder(
      Long branchId,
      UUID cashierUserId,
      String cashierEmail,
      PayOrderRequest request) {

    // 1. Verify Order in current branch
    CustomerOrderProvider.OrderBillingSummary order =
        customerOrderProvider
            .getOrderBillingSummary(branchId, request.orderId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Order " + request.orderId() + " not found in branch " + branchId));

    if ("PAID".equalsIgnoreCase(order.status())) {
      throw new IllegalStateException("Order " + request.orderId() + " has already been paid.");
    }

    if (invoiceRepository.findByOrderId(request.orderId()).isPresent()) {
      throw new IllegalStateException("An invoice already exists for order " + request.orderId());
    }

    // 2. Validate Payment Amounts
    BigDecimal totalPaid =
        request.payments().stream()
            .map(InvoicePaymentPayload::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalPaid.compareTo(order.totalAmount()) < 0) {
      throw new IllegalArgumentException(
          String.format(
              "Insufficient payment. Order total is %s, but total paid is %s.",
              order.totalAmount(), totalPaid));
    }

    BigDecimal changeAmount = totalPaid.subtract(order.totalAmount());

    // 3. Customer Handling (Idempotent upsert inside the transaction)
    Long customerId = null;
    String customerName = null;
    String customerDocType = null;
    String customerDocNum = null;

    if (request.customer() != null) {
      String docType = request.customer().identityDocumentType().trim().toUpperCase();
      String docNum = request.customer().identityDocumentNumber().trim();
      String name = request.customer().name().trim();

      OffsetDateTime now = OffsetDateTime.now();
      customerRepository.upsertCustomer(
          name, docType, docNum, request.customer().phoneNumber(), request.customer().address(), now);

      Customer customer =
          customerRepository
              .findByIdentityDocumentTypeAndIdentityDocumentNumber(docType, docNum)
              .orElseThrow(
                  () -> new IllegalStateException("Failed to resolve customer after upsert."));

      customerId = customer.id();
      customerName = customer.name();
      customerDocType = customer.identityDocumentType();
      customerDocNum = customer.identityDocumentNumber();
    }

    // 4. Generate next consecutive invoice number atomically (PostgreSQL row-level lock)
    String invoiceTypeStr = request.invoiceType().name();
    String invoiceNumber = generateConsecutiveInvoiceNumber(branchId, invoiceTypeStr);

    // 5. Persist Invoice and Payments
    Long cashShiftId =
        cashShiftProvider != null
            ? cashShiftProvider.findActiveShiftIdForCashier(branchId, cashierUserId).orElse(null)
            : null;

    Set<InvoicePayment> paymentEntities =
        request.payments().stream()
            .map(p -> new InvoicePayment(p.method().name(), p.amount(), p.reference()))
            .collect(Collectors.toSet());

    OffsetDateTime now = OffsetDateTime.now();
    Invoice invoice =
        new Invoice(
            null,
            branchId,
            cashShiftId,
            request.orderId(),
            customerId,
            invoiceNumber,
            invoiceTypeStr,
            order.totalAmount(),
            totalPaid,
            changeAmount,
            cashierUserId,
            cashierEmail,
            order.tableId(),
            order.tableName(),
            request.notes(),
            now,
            now,
            paymentEntities);

    Invoice savedInvoice = invoiceRepository.save(invoice);

    // 6. Mark Order as PAID and release table atomically
    customerOrderProvider.markOrderAsPaid(branchId, request.orderId());

    return toResponse(savedInvoice, customerName, customerDocType, customerDocNum);
  }

  @Transactional(readOnly = true)
  public InvoiceResponse getInvoiceById(Long branchId, Long invoiceId) {
    Invoice invoice =
        invoiceRepository
            .findByIdAndBranchId(invoiceId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Invoice " + invoiceId + " not found in branch " + branchId));

    return loadFullResponse(invoice);
  }

  @Transactional(readOnly = true)
  public InvoiceResponse getInvoiceByOrderId(Long branchId, Long orderId) {
    Invoice invoice =
        invoiceRepository
            .findByOrderId(orderId)
            .filter(inv -> branchId.equals(inv.branchId()))
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Invoice for order " + orderId + " not found in branch " + branchId));

    return loadFullResponse(invoice);
  }

  @Transactional(readOnly = true)
  public List<InvoiceResponse> listInvoices(Long branchId) {
    List<Invoice> invoices = invoiceRepository.findAllByBranchId(branchId);
    if (invoices.isEmpty()) {
      return Collections.emptyList();
    }

    Set<Long> customerIds =
        invoices.stream()
            .map(Invoice::customerId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<Long, Customer> customerMap = new HashMap<>();
    if (!customerIds.isEmpty()) {
      customerRepository.findAllById(customerIds).forEach(c -> customerMap.put(c.id(), c));
    }

    return invoices.stream()
        .map(
            inv -> {
              Customer c = inv.customerId() != null ? customerMap.get(inv.customerId()) : null;
              String customerName = c != null ? c.name() : null;
              String customerDocType = c != null ? c.identityDocumentType() : null;
              String customerDocNum = c != null ? c.identityDocumentNumber() : null;
              return toResponse(inv, customerName, customerDocType, customerDocNum);
            })
        .toList();
  }

  private String generateConsecutiveInvoiceNumber(Long branchId, String invoiceType) {
    Optional<InvoiceSeries> seriesOpt =
        invoiceSeriesRepository.findForUpdate(branchId, invoiceType);

    InvoiceSeries series;
    if (seriesOpt.isEmpty()) {
      String defaultPrefix =
          switch (invoiceType) {
            case "FACTURA" -> "F001";
            case "TICKET" -> "T001";
            default -> "B001";
          };
      invoiceSeriesRepository.initSeries(branchId, invoiceType, defaultPrefix, 0L);
      series =
          invoiceSeriesRepository
              .findForUpdate(branchId, invoiceType)
              .orElseThrow(() -> new IllegalStateException("Failed to initialize invoice series"));
    } else {
      series = seriesOpt.get();
    }

    long nextNumber = series.currentNumber() + 1;
    invoiceSeriesRepository.updateCurrentNumber(series.id(), nextNumber);

    return String.format("%s-%08d", series.series(), nextNumber);
  }

  private InvoiceResponse loadFullResponse(Invoice invoice) {
    String customerName = null;
    String customerDocType = null;
    String customerDocNum = null;

    if (invoice.customerId() != null) {
      Optional<Customer> c = customerRepository.findById(invoice.customerId());
      if (c.isPresent()) {
        customerName = c.get().name();
        customerDocType = c.get().identityDocumentType();
        customerDocNum = c.get().identityDocumentNumber();
      }
    }

    return toResponse(invoice, customerName, customerDocType, customerDocNum);
  }

  private InvoiceResponse toResponse(
      Invoice invoice, String customerName, String customerDocType, String customerDocNum) {

    List<InvoiceResponse.InvoicePaymentResponse> paymentResponses =
        invoice.payments() != null
            ? invoice.payments().stream()
                .map(
                    p ->
                        new InvoiceResponse.InvoicePaymentResponse(
                            p.id(), p.method(), p.amount(), p.reference()))
                .toList()
            : Collections.emptyList();

    return new InvoiceResponse(
        invoice.id(),
        invoice.branchId(),
        invoice.cashShiftId(),
        invoice.orderId(),
        invoice.customerId(),
        customerName,
        customerDocType,
        customerDocNum,
        invoice.invoiceNumber(),
        invoice.invoiceType(),
        invoice.orderTotal(),
        invoice.amountPaid(),
        invoice.changeAmount(),
        invoice.cashierUserId(),
        invoice.cashierEmail(),
        invoice.restaurantTableId(),
        invoice.restaurantTableName(),
        invoice.notes(),
        invoice.paidAt(),
        paymentResponses);
  }
}
