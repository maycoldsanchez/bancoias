package com.maycol.bancoias.infrastructure.exceptionhandler;

import com.maycol.bancoias.application.dto.ErrorResponse;
import com.maycol.bancoias.infrastructure.exception.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;
import java.time.OffsetDateTime;

@RestControllerAdvice public class ControllerAdvisor {

  @ExceptionHandler(TransferNotFoundException.class)
  Mono<ResponseEntity<ErrorResponse>> notFound(TransferNotFoundException e, ServerWebExchange x) {
    return response(
      HttpStatus.NOT_FOUND,
      e.code(),
      e.getMessage(),
      x
    );
  }

  @ExceptionHandler(BusinessException.class)
  Mono<ResponseEntity<ErrorResponse>> business(BusinessException e, ServerWebExchange x) {
    HttpStatus status = "IDEMPOTENCY_CONFLICT".equals(e.code()) ? HttpStatus.CONFLICT:HttpStatus.UNPROCESSABLE_ENTITY;
    return response(
      status,
      e.code(),
      e.getMessage(),
      x
    );
  }

  @ExceptionHandler(WebExchangeBindException.class)
  Mono<ResponseEntity<ErrorResponse>> validation(WebExchangeBindException e, ServerWebExchange x) {
    return response(
      HttpStatus.BAD_REQUEST,
      "INVALID_REQUEST",
      "La solicitud no cumple el formato requerido",
      x
    );
  }

  @ExceptionHandler(Exception.class)
  Mono<ResponseEntity<ErrorResponse>> unexpected(Exception e, ServerWebExchange x) {
    return response(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "UNEXPECTED_ERROR",
      "Ocurrió un error inesperado",
      x
    );
  }

  private Mono<ResponseEntity<ErrorResponse>> response(
    HttpStatus status,
    String code,
    String message,
    ServerWebExchange exchange
  ) {
    String correlationId = exchange.getResponse()
      .getHeaders()
      .getFirst("X-Correlation-Id");

    return Mono.just(ResponseEntity.status(status).body(new ErrorResponse(code,message,correlationId,OffsetDateTime.now())));
  }
}
