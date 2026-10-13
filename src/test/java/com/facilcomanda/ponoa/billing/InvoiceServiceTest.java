package com.facilcomanda.ponoa.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.billing.payload.CustomerPayload;
import com.facilcomanda.ponoa.billing.payload.InvoicePaymentPayload;
import com.facilcomanda.ponoa.billing.payload.InvoiceResponse;
import com.facilcomanda.ponoa.billing.payload.PayOrderRequest;
import com.facilcomanda.ponoa.sales.CustomerOrderProvider;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private InvoiceSeriesRepository invoiceSeriesRepository;
  @Mock private CustomerOrderProvider customerOrderProvider;

  private InvoiceService invoiceService;

  @BeforeEach
  void setUp() {
    invoiceService =
        new InvoiceService(
            customerRepository,
            invoiceRepository,
            invoiceSeriesRepository,
            customerOrderProvider);
  }

  @Test
  @DisplayName("Should pay order and create BOLETA with customer, updating series and marking order PAID")
  void shouldPayOrderSuccessfullyWithBoleta() {
    Long branchId = 1L;
    Long orderId = 100L;
    UUID cashierId = UUID.randomUUID();

    CustomerOrderProvider.OrderBillingSummary orderSummary =
        new CustomerOrderProvider.OrderBillingSummary(
            orderId, branchId, 5L, "Mesa 5", "DINE_IN", new BigDecimal("50.00"), "PENDING");

    when(customerOrderProvider.getOrderBillingSummary(branchId, orderId))
        .thenReturn(Optional.of(orderSummary));
    when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

    Customer savedCustomer =
        new Customer(
            20L,
            "YOVANA MAMANI",
            "DNI",
            "70135060",
            "999888777",
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber("DNI", "70135060"))
        .thenReturn(Optional.of(savedCustomer));

    InvoiceSeries existingSeries =
        new InvoiceSeries(1L, branchId, "BOLETA", "B001", 0L, OffsetDateTime.now());
    when(invoiceSeriesRepository.findForUpdate(branchId, "BOLETA"))
        .thenReturn(Optional.of(existingSeries));

    when(invoiceRepository.save(any(Invoice.class)))
        .thenAnswer(inv -> {
          Invoice input = inv.getArgument(0);
          return new Invoice(
              1L,
              input.branchId(),
              input.orderId(),
              input.customerId(),
              input.invoiceNumber(),
              input.invoiceType(),
              input.orderTotal(),
              input.amountPaid(),
              input.changeAmount(),
              input.cashierUserId(),
              input.cashierEmail(),
              input.restaurantTableId(),
              input.restaurantTableName(),
              input.notes(),
              input.paidAt(),
              input.createdAt(),
              input.payments());
        });

    PayOrderRequest request =
        new PayOrderRequest(
            orderId,
            InvoiceType.BOLETA,
            new CustomerPayload("YOVANA MAMANI", "DNI", "70135060", "999888777", null),
            List.of(new InvoicePaymentPayload(PaymentMethod.EFECTIVO, new BigDecimal("60.00"), null)),
            "Cobro en caja principal");

    InvoiceResponse response =
        invoiceService.payOrder(branchId, cashierId, "cajero@facilcomanda.com", request);

    assertThat(response).isNotNull();
    assertThat(response.invoiceNumber()).isEqualTo("B001-00000001");
    assertThat(response.orderTotal()).isEqualByComparingTo("50.00");
    assertThat(response.amountPaid()).isEqualByComparingTo("60.00");
    assertThat(response.changeAmount()).isEqualByComparingTo("10.00");
    assertThat(response.customerId()).isEqualTo(20L);
    assertThat(response.customerName()).isEqualTo("YOVANA MAMANI");

    // Verify series was incremented
    verify(invoiceSeriesRepository).updateCurrentNumber(1L, 1L);

    // Verify order was marked as PAID
    verify(customerOrderProvider).markOrderAsPaid(branchId, orderId);

    // Verify customer upsert was performed
    verify(customerRepository)
        .upsertCustomer(
            eq("YOVANA MAMANI"),
            eq("DNI"),
            eq("70135060"),
            eq("999888777"),
            eq(null),
            any());
  }

  @Test
  @DisplayName("Should generate FACTURA with F001-00000001 series")
  void shouldPayOrderSuccessfullyWithFactura() {
    Long branchId = 2L;
    Long orderId = 101L;
    UUID cashierId = UUID.randomUUID();

    CustomerOrderProvider.OrderBillingSummary orderSummary =
        new CustomerOrderProvider.OrderBillingSummary(
            orderId, branchId, null, null, "TAKEAWAY", new BigDecimal("120.00"), "PENDING");

    when(customerOrderProvider.getOrderBillingSummary(branchId, orderId))
        .thenReturn(Optional.of(orderSummary));
    when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

    Customer companyCustomer =
        new Customer(
            30L,
            "RVM MAQUINARIAS S.A.C.",
            "RUC",
            "20605100016",
            null,
            "Av. Industrial 456",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(customerRepository.findByIdentityDocumentTypeAndIdentityDocumentNumber("RUC", "20605100016"))
        .thenReturn(Optional.of(companyCustomer));

    InvoiceSeries existingSeries =
        new InvoiceSeries(2L, branchId, "FACTURA", "F001", 5L, OffsetDateTime.now());
    when(invoiceSeriesRepository.findForUpdate(branchId, "FACTURA"))
        .thenReturn(Optional.of(existingSeries));

    when(invoiceRepository.save(any(Invoice.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    PayOrderRequest request =
        new PayOrderRequest(
            orderId,
            InvoiceType.FACTURA,
            new CustomerPayload(
                "RVM MAQUINARIAS S.A.C.", "RUC", "20605100016", null, "Av. Industrial 456"),
            List.of(new InvoicePaymentPayload(PaymentMethod.TARJETA, new BigDecimal("120.00"), "TX-9988")),
            null);

    InvoiceResponse response =
        invoiceService.payOrder(branchId, cashierId, "cajero@facilcomanda.com", request);

    assertThat(response.invoiceNumber()).isEqualTo("F001-00000006");
    verify(invoiceSeriesRepository).updateCurrentNumber(2L, 6L);
    verify(customerOrderProvider).markOrderAsPaid(branchId, orderId);
  }

  @Test
  @DisplayName("Should reject payment if total amount paid is less than order total")
  void shouldRejectInsufficientPayment() {
    Long branchId = 1L;
    Long orderId = 102L;

    CustomerOrderProvider.OrderBillingSummary orderSummary =
        new CustomerOrderProvider.OrderBillingSummary(
            orderId, branchId, null, null, "DELIVERY", new BigDecimal("100.00"), "PENDING");

    when(customerOrderProvider.getOrderBillingSummary(branchId, orderId))
        .thenReturn(Optional.of(orderSummary));
    when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

    PayOrderRequest request =
        new PayOrderRequest(
            orderId,
            InvoiceType.BOLETA,
            null,
            List.of(new InvoicePaymentPayload(PaymentMethod.EFECTIVO, new BigDecimal("90.00"), null)),
            null);

    assertThatThrownBy(
            () ->
                invoiceService.payOrder(
                    branchId, UUID.randomUUID(), "cajero@facilcomanda.com", request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Insufficient payment");
  }

  @Test
  @DisplayName("Should reject payment if order was already PAID")
  void shouldRejectPaymentForAlreadyPaidOrder() {
    Long branchId = 1L;
    Long orderId = 103L;

    CustomerOrderProvider.OrderBillingSummary orderSummary =
        new CustomerOrderProvider.OrderBillingSummary(
            orderId, branchId, null, null, "DINE_IN", new BigDecimal("50.00"), "PAID");

    when(customerOrderProvider.getOrderBillingSummary(branchId, orderId))
        .thenReturn(Optional.of(orderSummary));

    PayOrderRequest request =
        new PayOrderRequest(
            orderId,
            InvoiceType.BOLETA,
            null,
            List.of(new InvoicePaymentPayload(PaymentMethod.EFECTIVO, new BigDecimal("50.00"), null)),
            null);

    assertThatThrownBy(
            () ->
                invoiceService.payOrder(
                    branchId, UUID.randomUUID(), "cajero@facilcomanda.com", request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already been paid");
  }

  @Test
  @DisplayName("Should support anonymous payment without customer (customer_id = null)")
  void shouldSupportAnonymousPayment() {
    Long branchId = 1L;
    Long orderId = 104L;

    CustomerOrderProvider.OrderBillingSummary orderSummary =
        new CustomerOrderProvider.OrderBillingSummary(
            orderId, branchId, null, null, "TAKEAWAY", new BigDecimal("25.00"), "PENDING");

    when(customerOrderProvider.getOrderBillingSummary(branchId, orderId))
        .thenReturn(Optional.of(orderSummary));
    when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

    InvoiceSeries existingSeries =
        new InvoiceSeries(3L, branchId, "BOLETA", "B001", 10L, OffsetDateTime.now());
    when(invoiceSeriesRepository.findForUpdate(branchId, "BOLETA"))
        .thenReturn(Optional.of(existingSeries));

    when(invoiceRepository.save(any(Invoice.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    PayOrderRequest request =
        new PayOrderRequest(
            orderId,
            InvoiceType.BOLETA,
            null, // No customer
            List.of(new InvoicePaymentPayload(PaymentMethod.YAPE, new BigDecimal("25.00"), "REF-1234")),
            null);

    InvoiceResponse response =
        invoiceService.payOrder(
            branchId, UUID.randomUUID(), "cajero@facilcomanda.com", request);

    assertThat(response.customerId()).isNull();
    assertThat(response.customerName()).isNull();
    assertThat(response.invoiceNumber()).isEqualTo("B001-00000011");
  }
}
