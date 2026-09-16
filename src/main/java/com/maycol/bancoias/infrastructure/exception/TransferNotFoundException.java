package com.maycol.bancoias.infrastructure.exception;

public class TransferNotFoundException extends BusinessException {

  public TransferNotFoundException() {
    super("TRANSFER_NOT_FOUND", "Transferencia no encontrada");
  }
}
