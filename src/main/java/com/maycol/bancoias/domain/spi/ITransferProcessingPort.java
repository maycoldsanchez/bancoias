package com.maycol.bancoias.domain.spi;

import com.maycol.bancoias.domain.model.Transfer;
import com.maycol.bancoias.domain.model.TransferCommand;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ITransferProcessingPort {
  Mono<Transfer> process(TransferCommand command);

  Mono<Transfer> findById(UUID id);
}
