package com.facilcomanda.ponoa.global.exception;

import java.net.URI;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    problemDetail.setTitle("Access Denied");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/forbidden"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problemDetail.setTitle("Resource not found");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/not-found"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(com.facilcomanda.ponoa.printing.PrintCooldownException.class)
  ProblemDetail handlePrintCooldown(com.facilcomanda.ponoa.printing.PrintCooldownException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    problemDetail.setTitle("Cooldown de Reimpresion Activo");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/cooldown"));
    problemDetail.setProperty("secondsRemaining", ex.getSecondsRemaining());
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(RateLimitExceededException.class)
  org.springframework.http.ResponseEntity<ProblemDetail> handleRateLimitExceeded(
      RateLimitExceededException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    problemDetail.setTitle("Limite de Consultas Excedido");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/rate-limit"));
    problemDetail.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
    problemDetail.setProperty("limitType", ex.getLimitType());
    problemDetail.setProperty("timestamp", Instant.now());

    return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .body(problemDetail);
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleGenericException(Exception ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred.");
    problemDetail.setTitle("Error Interno");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/internal-error"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ProblemDetail handleDuplicateKeyException(DuplicateKeyException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Your request has already been processed. Check your order history.");
    problemDetail.setTitle("Duplicate Operation");
    problemDetail.setType(URI.create("https://api.facilcomanda.com/errors/conflict"));
    problemDetail.setProperty("timestamp", Instant.now());
    return problemDetail;
  }
}
