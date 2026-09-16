package com.maycol.bancoias.domain.usecase;

import com.maycol.bancoias.domain.api.ITransferServicePort;
import com.maycol.bancoias.domain.model.Transfer;
import com.maycol.bancoias.domain.model.TransferCommand;
import com.maycol.bancoias.domain.spi.ITransferProcessingPort;
import reactor.core.publisher.Mono;
import java.util.UUID;

public class TransferUseCase implements ITransferServicePort {
  private final ITransferProcessingPort processor;

  public TransferUseCase(ITransferProcessingPort processor) {
    this.processor = processor;
  }

  public Mono<Transfer> create(TransferCommand command) {
    return processor.process(command);
  }

  public Mono<Transfer> findById(UUID id) {
    return processor.findById(id);
  }
}

