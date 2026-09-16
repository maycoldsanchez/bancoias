package com.maycol.bancoias.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Transfer(
  UUID id,
  String clientReference,
  String sourceAccount,
  String destinationAccount,
  BigDecimal amount,
  String currency,
  TransferStatus status,
  OffsetDateTime processedAt,
  String fingerprint
) {}
