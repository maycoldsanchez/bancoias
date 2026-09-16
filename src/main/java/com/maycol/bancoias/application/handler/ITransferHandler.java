package com.maycol.bancoias.application.handler;

import com.maycol.bancoias.application.dto.TransferRequest;
import com.maycol.bancoias.application.dto.TransferResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ITransferHandler {
  Mono<TransferResponse> create(TransferRequest request);

  Mono<TransferResponse> findById(UUID id);
}
