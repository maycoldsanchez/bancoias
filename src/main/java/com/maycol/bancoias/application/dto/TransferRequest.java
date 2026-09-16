package com.maycol.bancoias.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransferRequest(
  @NotBlank

  @Size(max=80) String clientReference,

  @NotBlank String sourceAccount,

  @NotBlank String destinationAccount,

  @NotNull
  @DecimalMin(value="0.01")
  @Digits(integer=16,fraction=2)
  BigDecimal amount,

  @NotBlank String currency
) { }

