package com.facilcomanda.ponoa.printing;

import com.facilcomanda.ponoa.sales.CustomerOrderProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      out.write(INIT);
      out.write(ALIGN_CENTER);

      if (isReprint) {
        out.write(BOLD_ON);
        out.write("================================\n".getBytes(StandardCharsets.US_ASCII));
        out.write("*** REIMPRESION / DUPLICADO ***\n".getBytes(StandardCharsets.US_ASCII));
        out.write("================================\n".getBytes(StandardCharsets.US_ASCII));
        out.write(BOLD_OFF);
      }

      if (isAddition) {
        out.write(BOLD_ON);
        out.write("================================\n".getBytes(StandardCharsets.US_ASCII));
        out.write("        *** ADICION ***\n".getBytes(StandardCharsets.US_ASCII));
        out.write("================================\n".getBytes(StandardCharsets.US_ASCII));
        out.write(BOLD_OFF);
      }

      out.write(BOLD_ON);
      out.write(DOUBLE_SIZE);

      if (PrintDocumentType.KITCHEN_ORDER.equals(documentType)) {
        String header =
            (targetArea != null && !targetArea.isBlank())
                ? "*** " + targetArea.toUpperCase() + " ***\n"
                : "*** COCINA ***\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
      } else {
        String header =
            (targetArea != null && !targetArea.isBlank())
                ? "*** CUENTA (" + targetArea.toUpperCase() + ") ***\n"
                : "*** CUENTA ***\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
      }

      out.write(NORMAL_SIZE);
      out.write(BOLD_OFF);
      out.write(("Sucursal: " + branchId + "\n").getBytes(StandardCharsets.US_ASCII));
      out.write(("Orden #: " + orderId + "\n").getBytes(StandardCharsets.US_ASCII));
      if (tableName != null && !tableName.isBlank()) {
        out.write(("Mesa: " + tableName + "\n").getBytes(StandardCharsets.US_ASCII));
      }
      if (orderType != null && !orderType.isBlank()) {
        out.write(("Tipo: " + orderType + "\n").getBytes(StandardCharsets.US_ASCII));
      }
      out.write(
          ("Hora: " + LocalDateTime.now().format(TIME_FORMATTER) + "\n")
              .getBytes(StandardCharsets.US_ASCII));
      if (notes != null && !notes.isBlank()) {
        out.write(("Notas: " + notes + "\n").getBytes(StandardCharsets.US_ASCII));
      }
      out.write("--------------------------------\n".getBytes(StandardCharsets.US_ASCII));

      out.write(ALIGN_LEFT);
      out.write("Detalle enviado a produccion.\n".getBytes(StandardCharsets.US_ASCII));
      out.write("--------------------------------\n".getBytes(StandardCharsets.US_ASCII));

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
        out.write("================================\n".getBytes(StandardCharsets.US_ASCII));
        out.write("*** REIMPRESION / DUPLICADO ***\n".getBytes(StandardCharsets.US_ASCII));
        out.write("================================\n".getBytes(StandardCharsets.US_ASCII));
        out.write(BOLD_OFF);
      }

      out.write(BOLD_ON);
      out.write(DOUBLE_SIZE);
      out.write("*** PRE-CUENTA ***\n".getBytes(StandardCharsets.US_ASCII));
      out.write(NORMAL_SIZE);
      out.write(BOLD_OFF);

      out.write(("Sucursal: " + branchId + "\n").getBytes(StandardCharsets.US_ASCII));
      out.write(("Orden #: " + orderId + "\n").getBytes(StandardCharsets.US_ASCII));

      if (summary != null) {
        if (summary.tableName() != null && !summary.tableName().isBlank()) {
          out.write(("Mesa: " + summary.tableName() + "\n").getBytes(StandardCharsets.US_ASCII));
        }
        if (summary.orderType() != null && !summary.orderType().isBlank()) {
          out.write(("Tipo: " + summary.orderType() + "\n").getBytes(StandardCharsets.US_ASCII));
        }
        if (summary.waiterOrCashierEmail() != null) {
          out.write(("Atendido: " + summary.waiterOrCashierEmail() + "\n").getBytes(StandardCharsets.US_ASCII));
        }
      }

      out.write(
          ("Fecha: " + LocalDateTime.now().format(TIME_FORMATTER) + "\n")
              .getBytes(StandardCharsets.US_ASCII));
      out.write("--------------------------------\n".getBytes(StandardCharsets.US_ASCII));

      out.write(ALIGN_LEFT);
      out.write("CANT  DESCRIPCION         TOTAL\n".getBytes(StandardCharsets.US_ASCII));
      out.write("--------------------------------\n".getBytes(StandardCharsets.US_ASCII));

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
          out.write(line.getBytes(StandardCharsets.US_ASCII));
        }
      }

      out.write("--------------------------------\n".getBytes(StandardCharsets.US_ASCII));

      if (summary != null) {
        out.write(String.format("SUBTOTAL:              S/ %7s\n", summary.subtotal()).getBytes(StandardCharsets.US_ASCII));
        out.write(String.format("I.G.V. (18%%):          S/ %7s\n", summary.taxAmount()).getBytes(StandardCharsets.US_ASCII));
        out.write(BOLD_ON);
        out.write(String.format("TOTAL:                 S/ %7s\n", summary.totalAmount()).getBytes(StandardCharsets.US_ASCII));
        out.write(BOLD_OFF);
        out.write(String.format("PROPINA SUG. (10%%):     S/ %7s\n", summary.suggestedTip10()).getBytes(StandardCharsets.US_ASCII));
      }

      out.write("================================\n".getBytes(StandardCharsets.US_ASCII));
      out.write(ALIGN_CENTER);
      out.write(BOLD_ON);
      out.write("*** NO VALIDO COMO COMPROBANTE ***\n".getBytes(StandardCharsets.US_ASCII));
      out.write("   GRACIAS POR SU VISITA   \n".getBytes(StandardCharsets.US_ASCII));
      out.write(BOLD_OFF);
      out.write("================================\n".getBytes(StandardCharsets.US_ASCII));

      out.write(FEED_AND_CUT);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to format ESC/POS pre-check ticket", e);
    }
  }
}
