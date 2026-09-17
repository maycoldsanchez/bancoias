package com.maycol.bancoias.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(

  @NotBlank
  @Size(max = 80)
  String clientReference,

  @NotBlank
  String sourceAccount,

  @NotBlank
  String destinationAccount,

  @NotNull
  @DecimalMin(value = "0.01")
  @Digits(integer = 16, fraction = 2)
  BigDecimal amount,

  @NotBlank
  String currency
) {
}