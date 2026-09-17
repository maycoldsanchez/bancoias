package com.maycol.bancoias.infrastructure.filter;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdWebFilter implements WebFilter {

  public static final String HEADER = "X-Correlation-Id";

  @Override
  public Mono<Void> filter(
    ServerWebExchange exchange,
    WebFilterChain chain
  ) {

    String requestedId =
      exchange.getRequest()
        .getHeaders()
        .getFirst(HEADER);

    String correlationId =
      (requestedId == null || requestedId.isBlank())
        ? UUID.randomUUID().toString()
        : requestedId;

    exchange.getResponse()
      .getHeaders()
      .set(HEADER, correlationId);

    return chain.filter(exchange)
      .contextWrite(context ->
        context.put("correlationId", correlationId)
      );
  }
}