package com.maycol.bancoias.domain.api;

import com.maycol.bancoias.domain.model.Transfer;
import com.maycol.bancoias.domain.model.TransferCommand;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ITransferServicePort {
  Mono<Transfer> create(TransferCommand command);

  Mono<Transfer> findById(UUID id);
}

