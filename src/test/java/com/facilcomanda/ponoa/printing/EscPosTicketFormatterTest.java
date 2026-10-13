package com.facilcomanda.ponoa.printing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EscPosTicketFormatterTest {

  private final EscPosTicketFormatter formatter = new EscPosTicketFormatter();

  @Test
  @DisplayName("Should format ESC/POS ticket with correct header, text content and cut command")
  void shouldFormatKitchenOrder() {
    byte[] bytes = formatter.formatTicket(1L, 100L, PrintDocumentType.KITCHEN_ORDER);

    assertThat(bytes).isNotEmpty();
    // Starts with ESC @ (0x1B, 0x40)
    assertThat(bytes[0]).isEqualTo((byte) 0x1B);
    assertThat(bytes[1]).isEqualTo((byte) 0x40);

    String text = new String(bytes, StandardCharsets.US_ASCII);
    assertThat(text).contains("*** COCINA ***");
    assertThat(text).contains("Sucursal: 1");
    assertThat(text).contains("Orden #: 100");

    // Ends with FEED_AND_CUT (0x1D, 0x56, 0x42, 0x03)
    int len = bytes.length;
    assertThat(bytes[len - 4]).isEqualTo((byte) 0x1D);
    assertThat(bytes[len - 3]).isEqualTo((byte) 0x56);
    assertThat(bytes[len - 2]).isEqualTo((byte) 0x42);
    assertThat(bytes[len - 1]).isEqualTo((byte) 0x03);
  }

  @Test
  @DisplayName("Should format ESC/POS ticket with custom production station header")
  void shouldFormatGrillOrder() {
    byte[] bytes = formatter.formatTicket(1L, 100L, PrintDocumentType.KITCHEN_ORDER, "PARRILLA");

    String text = new String(bytes, StandardCharsets.US_ASCII);
    assertThat(text).contains("*** PARRILLA ***");
  }

  @Test
  @DisplayName("Should format ESC/POS ticket with cashier terminal header")
  void shouldFormatCashierBill() {
    byte[] bytes = formatter.formatTicket(1L, 100L, PrintDocumentType.CUSTOMER_RECEIPT, "CAJA-01");

    String text = new String(bytes, StandardCharsets.US_ASCII);
    assertThat(text).contains("*** CUENTA (CAJA-01) ***");
  }

  @Test
  @DisplayName("Should format ESC/POS ticket with table name, dining type and notes")
  void shouldFormatTicketWithTableAndOrderNotes() {
    byte[] bytes =
        formatter.formatTicket(
            1L,
            100L,
            PrintDocumentType.KITCHEN_ORDER,
            "COCINA",
            "Mesa 4 (Terraza)",
            "DINE_IN",
            "Servir todo junto");

    String text = new String(bytes, StandardCharsets.US_ASCII);
    assertThat(text).contains("*** COCINA ***");
    assertThat(text).contains("Mesa: Mesa 4 (Terraza)");
    assertThat(text).contains("Tipo: DINE_IN");
    assertThat(text).contains("Notas: Servir todo junto");
  }

  @Test
  @DisplayName("Should format ESC/POS ticket with reprint banner when isReprint is true")
  void shouldFormatTicketWithReprintBanner() {
    byte[] bytes =
        formatter.formatTicket(
            1L,
            100L,
            PrintDocumentType.KITCHEN_ORDER,
            "COCINA",
            "Mesa 4",
            "DINE_IN",
            null,
            true);

    String text = new String(bytes, StandardCharsets.US_ASCII);
    assertThat(text).contains("*** REIMPRESION / DUPLICADO ***");
    assertThat(text).contains("================================");
    assertThat(text).contains("*** COCINA ***");
    assertThat(text).contains("Mesa: Mesa 4");
  }

  @Test
  @DisplayName("Should format ESC/POS ticket with addition banner when isAddition is true")
  void shouldFormatTicketWithAdditionBanner() {
    byte[] bytes =
        formatter.formatTicket(
            1L,
            100L,
            PrintDocumentType.KITCHEN_ORDER,
            "COCINA",
            "Mesa 4",
            "DINE_IN",
            "Ronda 2",
            false,
            true);

    String text = new String(bytes, StandardCharsets.US_ASCII);
    assertThat(text).contains("*** ADICION ***");
    assertThat(text).contains("================================");
    assertThat(text).contains("*** COCINA ***");
    assertThat(text).contains("Mesa: Mesa 4");
    assertThat(text).contains("Notas: Ronda 2");
  }
}
