package com.facilcomanda.ponoa.global.exception;

import com.facilcomanda.ponoa.printing.PrintCooldownException;
import com.facilcomanda.ponoa.printing.PrinterCommunicationException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
  public ProblemDetail handleAuthentication(org.springframework.security.core.AuthenticationException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    problemDetail.setTitle("Autenticacion Fallida");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/unauthorized"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    problemDetail.setTitle("Acceso Denegado");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/forbidden"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problemDetail.setTitle("Recurso No Encontrado");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/not-found"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail handleIllegalState(IllegalStateException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problemDetail.setTitle("Conflicto con Regla de Negocio");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/conflict"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problemDetail.setTitle("Solicitud Invalida");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/bad-request"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    problemDetail.setTitle("Stock Insuficiente");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/insufficient-stock"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(DuplicateKeyException.class)
  public ProblemDetail handleDuplicateKey(DuplicateKeyException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "La operación ya ha sido procesada o el recurso ya existe con los mismos datos únicos.");
    problemDetail.setTitle("Operacion Duplicada");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/conflict"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(PrintCooldownException.class)
  public ProblemDetail handlePrintCooldown(PrintCooldownException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    problemDetail.setTitle("Cooldown de Reimpresion Activo");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/cooldown"));
    problemDetail.setProperty("secondsRemaining", ex.getSecondsRemaining());
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    problemDetail.setTitle("Limite de Consultas Excedido");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/rate-limit"));
    problemDetail.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
    problemDetail.setProperty("limitType", ex.getLimitType());
    problemDetail.setProperty("timestamp", Instant.now());

    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .body(problemDetail);
  }

  @ExceptionHandler(PrinterCommunicationException.class)
  public ProblemDetail handlePrinterCommunication(PrinterCommunicationException ex) {
    log.warn("Printer hardware communication failed: {}", ex.getMessage());
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY,
            "Error de comunicación con la impresora física en red. Verifique papel, encendido o red.");
    problemDetail.setTitle("Falla de Comunicacion con Impresora");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/printer-offline"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @Override
  @Nullable
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {

    Map<String, String> errors = new HashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      errors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }

    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Uno o más campos enviados no cumplen con el formato o restricciones requeridas.");
    problemDetail.setTitle("Validacion de Datos Fallida");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/validation-failed"));
    problemDetail.setProperty("invalidParameters", errors);
    problemDetail.setProperty("timestamp", Instant.now());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(problemDetail);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleGenericException(Exception ex) {
    String errorId = UUID.randomUUID().toString();
    log.error("Unhandled server exception [errorId={}]: ", errorId, ex);

    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Ha ocurrido un error inesperado en el servidor. Comuníquese con soporte técnico indicando el ID de error.");
    problemDetail.setTitle("Error Interno del Servidor");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/internal-error"));
    problemDetail.setProperty("errorId", errorId);
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }
}
