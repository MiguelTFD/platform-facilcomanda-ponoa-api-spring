package com.facilcomanda.ponoa.printing;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

@Component
class EscPosTcpPrinterClient {

  @SuppressWarnings("rawtypes")
  private final CircuitBreakerFactory circuitBreakerFactory;

  @SuppressWarnings("rawtypes")
  EscPosTcpPrinterClient(CircuitBreakerFactory circuitBreakerFactory) {
    this.circuitBreakerFactory = circuitBreakerFactory;
  }

  public void print(String ipAddress, int port, byte[] bytes) {
    CircuitBreaker circuitBreaker = circuitBreakerFactory.create("printer-socket");

    circuitBreaker.run(
        () -> {
          sendDirect(ipAddress, port, bytes);
          return null;
        },
        throwable -> {
          if (throwable instanceof PrinterCommunicationException pce) {
            throw pce;
          }
          throw new PrinterCommunicationException(
              "Circuit breaker tripped or printer communication failed for "
                  + ipAddress
                  + ":"
                  + port,
              throwable);
        });
  }

  void sendDirect(String ipAddress, int port, byte[] bytes) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(ipAddress, port), 3000);
      socket.setSoTimeout(3000);
      OutputStream out = socket.getOutputStream();
      out.write(bytes);
      out.flush();
    } catch (IOException e) {
      throw new PrinterCommunicationException(
          "Socket connection failure to " + ipAddress + ":" + port, e);
    }
  }
}
