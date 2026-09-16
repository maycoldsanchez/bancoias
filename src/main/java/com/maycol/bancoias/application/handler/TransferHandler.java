package com.maycol.bancoias.application.handler;

import com.maycol.bancoias.application.dto.*;
import com.maycol.bancoias.application.mapper.TransferMapper;
import com.maycol.bancoias.domain.api.ITransferServicePort;
import reactor.core.publisher.Mono;
import java.util.UUID;

public class TransferHandler implements ITransferHandler {
  private final ITransferServicePort service;
  public TransferHandler(ITransferServicePort service) {
    this.service = service;
  }

  public Mono<TransferResponse> create(TransferRequest transferRequest) {
    return service.create(TransferMapper.toCommand(transferRequest))
      .map(TransferMapper::toResponse);
  }

  public Mono<TransferResponse> findById(UUID id) {
    return service.findById(id)
      .map(TransferMapper::toResponse);
  }
}

