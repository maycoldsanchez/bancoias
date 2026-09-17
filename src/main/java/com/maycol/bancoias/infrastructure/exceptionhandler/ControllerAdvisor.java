package com.maycol.bancoias.infrastructure.exceptionhandler;

import com.maycol.bancoias.application.dto.ErrorResponse;
import com.maycol.bancoias.infrastructure.exception.BusinessException;
import com.maycol.bancoias.infrastructure.exception.TransferNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class ControllerAdvisor {

  @ExceptionHandler(TransferNotFoundException.class)
  public Mono<ResponseEntity<ErrorResponse>> notFound(
    TransferNotFoundException exception,
    ServerWebExchange exchange) {

    return response(
      HttpStatus.NOT_FOUND,
      exception.code(),
      exception.getMessage(),
      exchange
    );
  }

  @ExceptionHandler(BusinessException.class)
  public Mono<ResponseEntity<ErrorResponse>> business(
    BusinessException exception,
    ServerWebExchange exchange) {

    HttpStatus status =
      "IDEMPOTENCY_CONFLICT".equals(exception.code())
        ? HttpStatus.CONFLICT
        : HttpStatus.UNPROCESSABLE_CONTENT;

    return response(
      status,
      exception.code(),
      exception.getMessage(),
      exchange
    );
  }

  @ExceptionHandler(WebExchangeBindException.class)
  public Mono<ResponseEntity<ErrorResponse>> validation(
    WebExchangeBindException exception,
    ServerWebExchange exchange) {

    return response(
      HttpStatus.BAD_REQUEST,
      "INVALID_REQUEST",
      "La solicitud no cumple el formato requerido",
      exchange
    );
  }

  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<ErrorResponse>> unexpected(
    Exception exception,
    ServerWebExchange exchange) {

    return response(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "UNEXPECTED_ERROR",
      "Ocurrió un error inesperado",
      exchange
    );
  }

  private Mono<ResponseEntity<ErrorResponse>> response(
    HttpStatus status,
    String code,
    String message,
    ServerWebExchange exchange) {

    String correlationId =
      exchange.getResponse()
        .getHeaders()
        .getFirst("X-Correlation-Id");

    ErrorResponse errorResponse = new ErrorResponse(
      code,
      message,
      correlationId,
      OffsetDateTime.now()
    );

    return Mono.just(
      ResponseEntity
        .status(status)
        .body(errorResponse)
    );
  }
}