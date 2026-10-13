package com.facilcomanda.ponoa.printing;

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
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      out.write(INIT);
      out.write(ALIGN_CENTER);
      out.write(BOLD_ON);
      out.write(DOUBLE_SIZE);

      if (PrintDocumentType.KITCHEN_ORDER.equals(documentType)) {
        out.write("*** COCINA ***\n".getBytes(StandardCharsets.US_ASCII));
      } else {
        out.write("*** CUENTA ***\n".getBytes(StandardCharsets.US_ASCII));
      }

      out.write(NORMAL_SIZE);
      out.write(BOLD_OFF);
      out.write(("Sucursal: " + branchId + "\n").getBytes(StandardCharsets.US_ASCII));
      out.write(("Orden #: " + orderId + "\n").getBytes(StandardCharsets.US_ASCII));
      out.write(
          ("Hora: " + LocalDateTime.now().format(TIME_FORMATTER) + "\n")
              .getBytes(StandardCharsets.US_ASCII));
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
}
