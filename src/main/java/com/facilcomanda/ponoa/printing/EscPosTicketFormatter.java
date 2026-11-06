package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.cash.payload.ZCutReportResponse;
import com.facilcomanda.ponoa.sales.CustomerOrderProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class EscPosTicketFormatter {

  private static final byte[] INIT = new byte[] {0x1B, 0x40};
  private static final byte[] ALIGN_CENTER = new byte[] {0x1B, 0x61, 0x01};
  private static final byte[] ALIGN_LEFT = new byte[] {0x1B, 0x61, 0x00};
  private static final byte[] BOLD_ON = new byte[] {0x1B, 0x45, 0x01};
  private static final byte[] BOLD_OFF = new byte[] {0x1B, 0x45, 0x00};
  private static final byte[] DOUBLE_SIZE = new byte[] {0x1D, 0x21, 0x11};
  private static final byte[] NORMAL_SIZE = new byte[] {0x1D, 0x21, 0x00};
  private static final byte[] FEED_AND_CUT = new byte[] {0x1D, 0x56, 0x42, 0x03};

  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static final java.nio.charset.Charset TICKET_CHARSET = StandardCharsets.ISO_8859_1;

  public byte[] formatTicket(
      Long branchId, Long orderId, PrintDocumentType documentType) {
    return formatTicket(branchId, orderId, documentType, null);
  }

  public byte[] formatTicket(
      Long branchId, Long orderId, PrintDocumentType documentType, String targetArea) {
    return formatTicket(branchId, orderId, documentType, targetArea, null, null, null);
  }

  public byte[] formatTicket(
      Long branchId,
      Long orderId,
      PrintDocumentType documentType,
      String targetArea,
      String tableName,
      String orderType,
      String notes) {
    return formatTicket(branchId, orderId, documentType, targetArea, tableName, orderType, notes, false);
  }

  public byte[] formatTicket(
      Long branchId,
      Long orderId,
      PrintDocumentType documentType,
      String targetArea,
      String tableName,
      String orderType,
      String notes,
      boolean isReprint) {
    return formatTicket(
        branchId, orderId, documentType, targetArea, tableName, orderType, notes, isReprint, false);
  }

  public byte[] formatTicket(
      Long branchId,
      Long orderId,
      PrintDocumentType documentType,
      String targetArea,
      String tableName,
      String orderType,
      String notes,
      boolean isReprint,
      boolean isAddition) {
    return formatTicket(
        branchId,
        orderId,
        documentType,
        targetArea,
        tableName,
        orderType,
        notes,
        isReprint,
        isAddition,
        java.util.List.of());
  }

  public byte[] formatTicket(
      Long branchId,
      Long orderId,
      PrintDocumentType documentType,
      String targetArea,
      String tableName,
      String orderType,
      String notes,
      boolean isReprint,
      boolean isAddition,
      java.util.List<CustomerOrderProvider.OrderPrintItem> items) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      out.write(INIT);
      out.write(ALIGN_CENTER);

      if (isReprint) {
        out.write(BOLD_ON);
        out.write("================================\n".getBytes(TICKET_CHARSET));
        out.write("*** REIMPRESION / DUPLICADO ***\n".getBytes(TICKET_CHARSET));
        out.write("================================\n".getBytes(TICKET_CHARSET));
        out.write(BOLD_OFF);
      }

      if (isAddition) {
        out.write(BOLD_ON);
        out.write("================================\n".getBytes(TICKET_CHARSET));
        out.write("        *** ADICION ***\n".getBytes(TICKET_CHARSET));
        out.write("================================\n".getBytes(TICKET_CHARSET));
        out.write(BOLD_OFF);
      }

      out.write(BOLD_ON);
      out.write(DOUBLE_SIZE);

      if (PrintDocumentType.KITCHEN_ORDER.equals(documentType)) {
        String header =
            (targetArea != null && !targetArea.isBlank())
                ? "*** " + targetArea.toUpperCase() + " ***\n"
                : "*** COCINA ***\n";
        out.write(header.getBytes(TICKET_CHARSET));
      } else {
        String header =
            (targetArea != null && !targetArea.isBlank())
                ? "*** CUENTA (" + targetArea.toUpperCase() + ") ***\n"
                : "*** CUENTA ***\n";
        out.write(header.getBytes(TICKET_CHARSET));
      }

      out.write(NORMAL_SIZE);
      out.write(BOLD_OFF);
      out.write(("Sucursal: " + branchId + "\n").getBytes(TICKET_CHARSET));
      out.write(("Orden #: " + orderId + "\n").getBytes(TICKET_CHARSET));
      if (tableName != null && !tableName.isBlank()) {
        out.write(("Mesa: " + tableName + "\n").getBytes(TICKET_CHARSET));
      }
      if (orderType != null && !orderType.isBlank()) {
        out.write(("Tipo: " + orderType + "\n").getBytes(TICKET_CHARSET));
      }
      out.write(
          ("Hora: " + LocalDateTime.now().format(TIME_FORMATTER) + "\n")
              .getBytes(TICKET_CHARSET));
      if (notes != null && !notes.isBlank()) {
        out.write(("Notas: " + notes + "\n").getBytes(TICKET_CHARSET));
      }
      out.write("--------------------------------\n".getBytes(TICKET_CHARSET));

      out.write(ALIGN_LEFT);
      if (items != null && !items.isEmpty()) {
        java.util.List<CustomerOrderProvider.OrderPrintItem> filteredItems = items;
        if (PrintDocumentType.KITCHEN_ORDER.equals(documentType)
            && targetArea != null
            && !targetArea.isBlank()) {
          java.util.List<CustomerOrderProvider.OrderPrintItem> areaItems =
              items.stream()
                  .filter(
                      i ->
                          i.productionArea() != null
                              && i.productionArea().equalsIgnoreCase(targetArea.trim()))
                  .toList();
          if (!areaItems.isEmpty()) {
            filteredItems = areaItems;
          }
        }
        for (CustomerOrderProvider.OrderPrintItem item : filteredItems) {
          String itemName = item.name() != null ? item.name() : ("SKU-" + item.skuId());
          out.write((item.quantity() + "x " + itemName + "\n").getBytes(TICKET_CHARSET));
          if (item.comments() != null && !item.comments().isBlank()) {
            out.write(("   > " + item.comments() + "\n").getBytes(TICKET_CHARSET));
          }
        }
      } else {
        out.write("Detalle enviado a produccion.\n".getBytes(TICKET_CHARSET));
      }
      out.write("--------------------------------\n".getBytes(TICKET_CHARSET));

      out.write(FEED_AND_CUT);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to format ESC/POS ticket", e);
    }
  }

  public byte[] formatPreCheckTicket(
      Long branchId,
      Long orderId,
      CustomerOrderProvider.OrderPreCheckSummary summary,
      boolean isReprint) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      out.write(INIT);
      out.write(ALIGN_CENTER);

      if (isReprint) {
        out.write(BOLD_ON);
        out.write("================================\n".getBytes(TICKET_CHARSET));
        out.write("*** REIMPRESION / DUPLICADO ***\n".getBytes(TICKET_CHARSET));
        out.write("================================\n".getBytes(TICKET_CHARSET));
        out.write(BOLD_OFF);
      }

      out.write(BOLD_ON);
      out.write(DOUBLE_SIZE);
      out.write("*** PRE-CUENTA ***\n".getBytes(TICKET_CHARSET));
      out.write(NORMAL_SIZE);
      out.write(BOLD_OFF);

      out.write(("Sucursal: " + branchId + "\n").getBytes(TICKET_CHARSET));
      out.write(("Orden #: " + orderId + "\n").getBytes(TICKET_CHARSET));

      if (summary != null) {
        if (summary.tableName() != null && !summary.tableName().isBlank()) {
          out.write(("Mesa: " + summary.tableName() + "\n").getBytes(TICKET_CHARSET));
        }
        if (summary.orderType() != null && !summary.orderType().isBlank()) {
          out.write(("Tipo: " + summary.orderType() + "\n").getBytes(TICKET_CHARSET));
        }
        if (summary.waiterOrCashierEmail() != null) {
          out.write(("Atendido: " + summary.waiterOrCashierEmail() + "\n").getBytes(TICKET_CHARSET));
        }
      }

      out.write(
          ("Fecha: " + LocalDateTime.now().format(TIME_FORMATTER) + "\n")
              .getBytes(TICKET_CHARSET));
      out.write("--------------------------------\n".getBytes(TICKET_CHARSET));

      out.write(ALIGN_LEFT);
      out.write("CANT  DESCRIPCION         TOTAL\n".getBytes(TICKET_CHARSET));
      out.write("--------------------------------\n".getBytes(TICKET_CHARSET));

      if (summary != null && summary.items() != null) {
        for (CustomerOrderProvider.PreCheckItem item : summary.items()) {
          String name = item.name() != null ? item.name() : ("SKU-" + item.skuId());
          if (name.length() > 16) {
            name = name.substring(0, 16);
          }
          String line =
              String.format(
                  "%-4s %-16s %9s\n",
                  item.quantity() + "x",
                  name,
                  "S/ " + item.lineTotal());
          out.write(line.getBytes(TICKET_CHARSET));
        }
      }

      out.write("--------------------------------\n".getBytes(TICKET_CHARSET));

      if (summary != null) {
        out.write(String.format("SUBTOTAL:              S/ %7s\n", summary.subtotal()).getBytes(TICKET_CHARSET));
        out.write(String.format("I.G.V. (18%%):          S/ %7s\n", summary.taxAmount()).getBytes(TICKET_CHARSET));
        out.write(BOLD_ON);
        out.write(String.format("TOTAL:                 S/ %7s\n", summary.totalAmount()).getBytes(TICKET_CHARSET));
        out.write(BOLD_OFF);
        out.write(String.format("PROPINA SUG. (10%%):     S/ %7s\n", summary.suggestedTip10()).getBytes(TICKET_CHARSET));
      }

      out.write("================================\n".getBytes(TICKET_CHARSET));
      out.write(ALIGN_CENTER);
      out.write(BOLD_ON);
      out.write("*** NO VALIDO COMO COMPROBANTE ***\n".getBytes(TICKET_CHARSET));
      out.write("   GRACIAS POR SU VISITA   \n".getBytes(TICKET_CHARSET));
      out.write(BOLD_OFF);
      out.write("================================\n".getBytes(TICKET_CHARSET));

      out.write(FEED_AND_CUT);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to format ESC/POS pre-check ticket", e);
    }
  }

  public byte[] formatZCutTicket(ZCutReportResponse zCut) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      out.write(INIT);
      out.write(ALIGN_CENTER);
      out.write(BOLD_ON);
      out.write(DOUBLE_SIZE);
      out.write("FACILCOMANDA\n".getBytes(TICKET_CHARSET));
      out.write(NORMAL_SIZE);
      out.write("CORTE Z - CIERRE DE TURNO\n".getBytes(TICKET_CHARSET));
      out.write(BOLD_OFF);
      out.write("================================\n".getBytes(TICKET_CHARSET));
      out.write(ALIGN_LEFT);

      out.write(String.format("SUCURSAL: %s\n", zCut.branchName()).getBytes(TICKET_CHARSET));
      out.write(String.format("CAJA:     %s\n", zCut.cashRegisterName()).getBytes(TICKET_CHARSET));
      out.write(String.format("TURNO ID: #%d (%s)\n", zCut.shiftId(), zCut.status()).getBytes(TICKET_CHARSET));
      out.write(String.format("CAJERO:   %s\n", zCut.cashierName()).getBytes(TICKET_CHARSET));
      if (zCut.openedAt() != null) {
        out.write(String.format("APERTURA: %s\n", zCut.openedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).getBytes(TICKET_CHARSET));
      }
      if (zCut.closedAt() != null) {
        out.write(String.format("CIERRE:   %s\n", zCut.closedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).getBytes(TICKET_CHARSET));
      }

      out.write("--------------------------------\n".getBytes(TICKET_CHARSET));
      out.write(BOLD_ON);
      out.write(String.format("FONDO INICIAL:     S/ %9s\n", zCut.openingAmount()).getBytes(TICKET_CHARSET));
      out.write(BOLD_OFF);
      out.write("--------------------------------\n".getBytes(TICKET_CHARSET));
      out.write(BOLD_ON);
      out.write("VENTAS POR MEDIO DE PAGO:\n".getBytes(TICKET_CHARSET));
      out.write(BOLD_OFF);

      if (zCut.salesByPaymentMethod() != null) {
        for (Map.Entry<String, BigDecimal> entry : zCut.salesByPaymentMethod().entrySet()) {
          out.write(String.format("%-18s S/ %9s\n", entry.getKey() + ":", entry.getValue()).getBytes(TICKET_CHARSET));
        }
      }

      out.write("--------------------------------\n".getBytes(TICKET_CHARSET));
      out.write(BOLD_ON);
      out.write(String.format("TOTAL VENTAS:      S/ %9s\n", zCut.totalSalesAmount()).getBytes(TICKET_CHARSET));
      out.write(String.format("TOTAL COMPROBANTES: %11d\n", zCut.totalInvoicesCount()).getBytes(TICKET_CHARSET));
      out.write(BOLD_OFF);

      out.write("--------------------------------\n".getBytes(TICKET_CHARSET));
      out.write(BOLD_ON);
      out.write("ARQUEO DE EFECTIVO:\n".getBytes(TICKET_CHARSET));
      out.write(BOLD_OFF);
      out.write(String.format("Efectivo Ventas:   S/ %9s\n", zCut.netCashFromSales()).getBytes(TICKET_CHARSET));
      out.write(String.format("Ingresos Menores:  S/ %9s\n", zCut.totalIncomes()).getBytes(TICKET_CHARSET));
      out.write(String.format("Gastos Menores:    S/ %9s\n", zCut.totalExpenses()).getBytes(TICKET_CHARSET));
      out.write(BOLD_ON);
      out.write(String.format("EFECTIVO ESPERADO: S/ %9s\n", zCut.expectedCash()).getBytes(TICKET_CHARSET));
      if (zCut.countedCash() != null) {
        out.write(String.format("EFECTIVO CONTADO:  S/ %9s\n", zCut.countedCash()).getBytes(TICKET_CHARSET));
      }
      if (zCut.cashDifference() != null) {
        String diffSign = zCut.cashDifference().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        out.write(String.format("DIFERENCIA:        S/ %8s%s\n", diffSign, zCut.cashDifference()).getBytes(TICKET_CHARSET));
      }
      out.write(BOLD_OFF);

      out.write("================================\n".getBytes(TICKET_CHARSET));
      out.write("\n\n\n".getBytes(TICKET_CHARSET));
      out.write("   -------------------------\n".getBytes(TICKET_CHARSET));
      out.write(ALIGN_CENTER);
      out.write(String.format("   %s\n", zCut.cashierName()).getBytes(TICKET_CHARSET));
      out.write("   FIRMA DE CONFORMIDAD\n".getBytes(TICKET_CHARSET));
      out.write("================================\n".getBytes(TICKET_CHARSET));

      out.write(FEED_AND_CUT);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to format ESC/POS Z-cut ticket", e);
    }
  }
}

