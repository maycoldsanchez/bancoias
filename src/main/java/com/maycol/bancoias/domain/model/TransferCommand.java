package com.maycol.bancoias.domain.model;

import java.math.BigDecimal;

public record TransferCommand(
  String clientReference,
  String sourceAccount,
  String destinationAccount,
  BigDecimal amount,
  String currency
) {}