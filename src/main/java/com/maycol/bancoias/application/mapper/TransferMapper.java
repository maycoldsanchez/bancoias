package com.maycol.bancoias.application.mapper;

import com.maycol.bancoias.application.dto.*;
import com.maycol.bancoias.domain.model.*;

public final class TransferMapper {
  private TransferMapper() {
  }

  public static TransferCommand toCommand(TransferRequest transferRequest) {
    return new TransferCommand(
      transferRequest.clientReference(),
      transferRequest.sourceAccount(),
      transferRequest.destinationAccount(),
      transferRequest.amount(),
      transferRequest.currency()
    );
  }

  public static TransferResponse toResponse(Transfer transfer) {
    return new TransferResponse(
      transfer.id(),
      transfer.clientReference(),
      mask(transfer.sourceAccount()),
      mask(transfer.destinationAccount()),
      transfer.amount(),
      transfer.currency(),
      transfer.status().name(),
      transfer.processedAt());
  }

  private static String mask(String account) {
    return account.length() <= 4 ? "****" : "****" + account.substring(account.length() - 4);
  }
}

