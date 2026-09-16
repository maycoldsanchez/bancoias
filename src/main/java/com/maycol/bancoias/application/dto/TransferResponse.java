package com.maycol.bancoias.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferResponse(
  UUID transferId,
  String clientReference,
  String sourceAccount,
  String destinationAccount,
  BigDecimal amount,
  String currency,
  String status,
  OffsetDateTime processedAt
) { }

