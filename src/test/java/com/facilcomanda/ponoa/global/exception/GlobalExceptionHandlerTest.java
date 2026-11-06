package com.facilcomanda.ponoa.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.printing.PrintCooldownException;
import com.facilcomanda.ponoa.printing.PrinterCommunicationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  @Test
  @DisplayName("Should handle IdentityProviderUnavailableException as 502 BAD_GATEWAY")
  void shouldHandleIdentityProviderUnavailableException() {
    IdentityProviderUnavailableException ex =
        new IdentityProviderUnavailableException("Connection refused", new RuntimeException());

    ProblemDetail problem = handler.handleIdentityProviderUnavailable(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    assertThat(problem.getTitle()).isEqualTo("Proveedor de Identidades No Disponible");
    assertThat(problem.getDetail()).contains("Connection refused");
  }

  @Test
  @DisplayName("Should handle IllegalStateException as 409 CONFLICT")
  void shouldHandleIllegalStateException() {
    IllegalStateException ex = new IllegalStateException("Cannot deactivate table while it is occupied");

    ProblemDetail problem = handler.handleIllegalState(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problem.getTitle()).isEqualTo("Conflicto con Regla de Negocio");
    assertThat(problem.getDetail()).isEqualTo("Cannot deactivate table while it is occupied");
    assertThat(problem.getProperties()).containsKey("timestamp");
  }

  @Test
  @DisplayName("Should handle IllegalArgumentException as 400 BAD_REQUEST")
  void shouldHandleIllegalArgumentException() {
    IllegalArgumentException ex = new IllegalArgumentException("Invalid document format");

    ProblemDetail problem = handler.handleIllegalArgument(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getTitle()).isEqualTo("Solicitud Invalida");
    assertThat(problem.getDetail()).isEqualTo("Invalid document format");
  }

  @Test
  @DisplayName("Should handle InsufficientStockException as 422 UNPROCESSABLE_ENTITY")
  void shouldHandleInsufficientStockException() {
    InsufficientStockException ex = new InsufficientStockException("Insufficient stock for SKU P-01");

    ProblemDetail problem = handler.handleInsufficientStock(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
    assertThat(problem.getTitle()).isEqualTo("Stock Insuficiente");
    assertThat(problem.getDetail()).isEqualTo("Insufficient stock for SKU P-01");
  }

  @Test
  @DisplayName("Should handle PrinterCommunicationException as 502 BAD_GATEWAY")
  void shouldHandlePrinterCommunicationException() {
    PrinterCommunicationException ex =
        new PrinterCommunicationException("Connection refused", new RuntimeException());

    ProblemDetail problem = handler.handlePrinterCommunication(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    assertThat(problem.getTitle()).isEqualTo("Falla de Comunicacion con Impresora");
  }

  @Test
  @DisplayName("Should handle DuplicateKeyException as 409 CONFLICT")
  void shouldHandleDuplicateKeyException() {
    DuplicateKeyException ex = new DuplicateKeyException("Duplicate key violation");

    ProblemDetail problem = handler.handleDuplicateKey(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problem.getTitle()).isEqualTo("Operacion Duplicada");
  }

  @Test
  @DisplayName("Should handle ResourceNotFoundException as 404 NOT_FOUND")
  void shouldHandleResourceNotFoundException() {
    ResourceNotFoundException ex = new ResourceNotFoundException("Order 123 not found");

    ProblemDetail problem = handler.handleResourceNotFound(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(problem.getTitle()).isEqualTo("Recurso No Encontrado");
  }

  @Test
  @DisplayName("Should handle AccessDeniedException as 403 FORBIDDEN")
  void shouldHandleAccessDeniedException() {
    AccessDeniedException ex = new AccessDeniedException("Access forbidden to branch");

    ProblemDetail problem = handler.handleAccessDenied(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(problem.getTitle()).isEqualTo("Acceso Denegado");
  }

  @Test
  @DisplayName("Should handle PrintCooldownException as 429 TOO_MANY_REQUESTS with remaining seconds")
  void shouldHandlePrintCooldownException() {
    PrintCooldownException ex = new PrintCooldownException(12);

    ProblemDetail problem = handler.handlePrintCooldown(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(problem.getTitle()).isEqualTo("Cooldown de Reimpresion Activo");
    assertThat(problem.getProperties().get("secondsRemaining")).isEqualTo(12L);
  }

  @Test
  @DisplayName("Should handle RateLimitExceededException with Retry-After header")
  void shouldHandleRateLimitExceededException() {
    RateLimitExceededException ex =
        new RateLimitExceededException("Too many requests", 60L, "USER_SLIDING_WINDOW");

    ResponseEntity<ProblemDetail> response = handler.handleRateLimitExceeded(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
    assertThat(response.getBody().getProperties().get("limitType")).isEqualTo("USER_SLIDING_WINDOW");
  }

  @Test
  @DisplayName("Should handle Generic Exception as 500 with generated errorId")
  void shouldHandleGenericException() {
    RuntimeException ex = new RuntimeException("Unexpected internal failure");

    ProblemDetail problem = handler.handleGenericException(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(problem.getTitle()).isEqualTo("Error Interno del Servidor");
    assertThat(problem.getProperties()).containsKey("errorId");
    assertThat(problem.getProperties().get("errorId")).isNotNull();
  }

  @Test
  @DisplayName("Should handle MethodArgumentNotValid with invalidParameters map")
  void shouldHandleMethodArgumentNotValid() {
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError = new FieldError("user", "email", "must not be blank");

    when(ex.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

    ResponseEntity<Object> response =
        handler.handleMethodArgumentNotValid(
            ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);

    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertThat(problem.getTitle()).isEqualTo("Validacion de Datos Fallida");

    @SuppressWarnings("unchecked")
    Map<String, String> invalidParams =
        (Map<String, String>) problem.getProperties().get("invalidParameters");
    assertThat(invalidParams).containsEntry("email", "must not be blank");
  }

  @Test
  @DisplayName("Should handle MethodArgumentTypeMismatch as 400 BAD_REQUEST")
  void shouldHandleMethodArgumentTypeMismatch() {
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
        new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
            "abc", Long.class, "orderId", null, null);

    ProblemDetail problem = handler.handleMethodArgumentTypeMismatch(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getTitle()).isEqualTo("Tipo de Parámetro Inválido");
    assertThat(problem.getProperties().get("parameterName")).isEqualTo("orderId");
    assertThat(problem.getProperties().get("invalidValue")).isEqualTo("abc");
  }

  @Test
  @DisplayName("Should handle MissingRequestHeaderException as 400 BAD_REQUEST")
  void shouldHandleMissingRequestHeaderException() throws Exception {
    java.lang.reflect.Method method =
        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class);
    org.springframework.core.MethodParameter param =
        new org.springframework.core.MethodParameter(method, 0);
    org.springframework.web.bind.MissingRequestHeaderException ex =
        new org.springframework.web.bind.MissingRequestHeaderException("X-Branch-Id", param);

    ProblemDetail problem = handler.handleMissingRequestHeader(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getTitle()).isEqualTo("Encabezado HTTP Requerido Faltante");
    assertThat(problem.getProperties().get("headerName")).isEqualTo("X-Branch-Id");
  }

  @Test
  @DisplayName("Should handle HttpMessageNotReadable as 400 BAD_REQUEST")
  void shouldHandleHttpMessageNotReadable() {
    org.springframework.http.converter.HttpMessageNotReadableException ex =
        new org.springframework.http.converter.HttpMessageNotReadableException(
            "Malformed JSON", mock(org.springframework.http.HttpInputMessage.class));

    ResponseEntity<Object> response =
        handler.handleHttpMessageNotReadable(
            ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);

    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertThat(problem.getTitle()).isEqualTo("Cuerpo de Solicitud Inválido");
  }

  private void dummyMethod(String header) {}
}
