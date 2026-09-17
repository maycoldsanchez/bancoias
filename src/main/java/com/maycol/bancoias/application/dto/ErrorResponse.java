package com.maycol.bancoias.application.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
  String code,
  String message,
  String correlationId,
  OffsetDateTime timestamp
) {
}