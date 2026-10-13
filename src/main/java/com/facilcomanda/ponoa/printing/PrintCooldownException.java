package com.facilcomanda.ponoa.printing;

public class PrintCooldownException extends RuntimeException {

  private final long secondsRemaining;

  public PrintCooldownException(long secondsRemaining) {
    super(
        "Debes esperar al menos 1 minuto antes de realizar una reimpresion para esta orden. Intentalo de nuevo en "
            + secondsRemaining
            + " segundos.");
    this.secondsRemaining = secondsRemaining;
  }

  public long getSecondsRemaining() {
    return secondsRemaining;
  }
}
