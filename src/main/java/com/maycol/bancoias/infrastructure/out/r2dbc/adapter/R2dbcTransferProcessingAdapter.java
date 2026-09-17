package com.maycol.bancoias.infrastructure.out.r2dbc.adapter;

import com.maycol.bancoias.domain.model.Transfer;
import com.maycol.bancoias.domain.model.TransferCommand;
import com.maycol.bancoias.domain.spi.ITransferProcessingPort;
import com.maycol.bancoias.infrastructure.exception.BusinessException;
import com.maycol.bancoias.infrastructure.exception.TransferNotFoundException;
import com.maycol.bancoias.infrastructure.out.r2dbc.mapper.TransferEntityMapper;
import com.maycol.bancoias.infrastructure.out.r2dbc.repository.IAccountRepository;
import com.maycol.bancoias.infrastructure.out.r2dbc.repository.ITransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class R2dbcTransferProcessingAdapter
  implements ITransferProcessingPort {

  private final ITransferRepository transferRepository;
  private final IAccountRepository accountRepository;
  private final TransferEntityMapper transferEntityMapper;
  private final TransactionalOperator tx;

  @Override
  public Mono<Transfer> process(TransferCommand command) {

    return Mono.defer(() ->
      validate(command)
        .then(findByReference(command.clientReference())
          .flatMap(existing ->
            sameRequest(existing, command)
              ? Mono.just(existing)
              : Mono.error(new BusinessException(
              "IDEMPOTENCY_CONFLICT",
              "clientReference ya fue usada con datos diferentes"
            ))
          )
          .switchIfEmpty(createNew(command)))
    ).onErrorResume(
      DataIntegrityViolationException.class,
      e -> findByReference(command.clientReference())
        .switchIfEmpty(Mono.error(e))
        .flatMap(existing ->
          sameRequest(existing, command)
            ? Mono.just(existing)
            : Mono.error(new BusinessException(
            "IDEMPOTENCY_CONFLICT",
            "clientReference ya fue usada con datos diferentes"
          ))
        )
    );
  }

  private Mono<Transfer> createNew(TransferCommand command) {

    UUID id = UUID.randomUUID();
    String fingerprint = fingerprint(command);

    return tx.transactional(
      validateAccounts(command)
        .then(insert(id, command, fingerprint))
        .then(debit(command))
        .then(credit(command))
        .then(markCompleted(id, OffsetDateTime.now()))
        .then(findById(id))
    );
  }

  private Mono<Void> validate(TransferCommand command) {

    if (!"COP".equals(command.currency())) {
      return Mono.error(new BusinessException(
        "INVALID_CURRENCY",
        "La moneda debe ser COP"
      ));
    }

    if (command.sourceAccount().equals(command.destinationAccount())) {
      return Mono.error(new BusinessException(
        "SAME_ACCOUNT",
        "La cuenta origen y destino deben ser diferentes"
      ));
    }

    if (command.amount().scale() > 2
      || command.amount().compareTo(BigDecimal.ZERO) <= 0) {

      return Mono.error(new BusinessException(
        "INVALID_AMOUNT",
        "El monto debe ser mayor que cero y tener máximo dos decimales"
      ));
    }

    return Mono.empty();
  }

  private Mono<Void> validateAccounts(TransferCommand command) {

    return accountRepository
      .findByAccountNumber(command.sourceAccount())
      .switchIfEmpty(Mono.error(new BusinessException(
        "SOURCE_ACCOUNT_NOT_FOUND",
        "La cuenta origen no existe"
      )))
      .flatMap(account ->
        "ACTIVE".equals(account.getStatus())
          ? Mono.empty()
          : Mono.error(new BusinessException(
          "SOURCE_ACCOUNT_INACTIVE",
          "La cuenta origen no está activa"
        ))
      )
      .then(
        accountRepository
          .findByAccountNumber(command.destinationAccount())
          .switchIfEmpty(Mono.error(new BusinessException(
            "DESTINATION_ACCOUNT_NOT_FOUND",
            "La cuenta destino no existe"
          )))
          .flatMap(account ->
            "ACTIVE".equals(account.getStatus())
              ? Mono.empty()
              : Mono.error(new BusinessException(
              "DESTINATION_ACCOUNT_INACTIVE",
              "La cuenta destino no está activa"
            ))
          )
      );
  }

  private Mono<Void> insert(
    UUID id,
    TransferCommand command,
    String fingerprint
  ) {

    return transferRepository
      .insert(
        id,
        command.clientReference(),
        fingerprint,
        command.sourceAccount(),
        command.destinationAccount(),
        command.amount(),
        command.currency(),
        "PENDING",
        null
      )
      .flatMap(rowsUpdated ->
        rowsUpdated == 1
          ? Mono.empty()
          : Mono.error(new BusinessException(
          "TRANSFER_CREATION_FAILED",
          "No fue posible crear la transferencia"
        ))
      );
  }

  private Mono<Void> debit(TransferCommand command) {

    return accountRepository
      .debit(
        command.sourceAccount(),
        command.amount()
      )
      .flatMap(rowsUpdated ->
        rowsUpdated == 1
          ? Mono.empty()
          : Mono.error(new BusinessException(
          "INSUFFICIENT_BALANCE",
          "Saldo insuficiente"
        ))
      )
      .then();
  }

  private Mono<Void> credit(TransferCommand command) {

    return accountRepository
      .credit(
        command.destinationAccount(),
        command.amount()
      )
      .flatMap(rowsUpdated ->
        rowsUpdated == 1
          ? Mono.empty()
          : Mono.error(new BusinessException(
          "DESTINATION_ACCOUNT_INACTIVE",
          "La cuenta destino no está activa"
        ))
      )
      .then();
  }

  private Mono<Void> markCompleted(
    UUID id,
    OffsetDateTime now
  ) {

    return transferRepository
      .markCompleted(id, now)
      .flatMap(rowsUpdated ->
        rowsUpdated == 1
          ? Mono.empty()
          : Mono.error(new TransferNotFoundException())
      );
  }

  @Override
  public Mono<Transfer> findById(UUID id) {

    return transferRepository
      .findById(id)
      .map(transferEntityMapper::toTransfer)
      .switchIfEmpty(Mono.error(
        new TransferNotFoundException()
      ));
  }

  private Mono<Transfer> findByReference(String clientReference) {

    return transferRepository
      .findByClientReference(clientReference)
      .map(transferEntityMapper::toTransfer);
  }

  private boolean sameRequest(
    Transfer transfer,
    TransferCommand command
  ) {
    return transfer.fingerprint()
      .equals(fingerprint(command));
  }

  private String fingerprint(TransferCommand command) {

    try {

      String payload = String.join(
        "|",
        command.clientReference(),
        command.sourceAccount(),
        command.destinationAccount(),
        command.amount()
          .setScale(2, RoundingMode.UNNECESSARY)
          .toPlainString(),
        command.currency()
      );

      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256")
          .digest(
            payload.getBytes(StandardCharsets.UTF_8)
          )
      );

    } catch (Exception e) {

      throw new IllegalStateException(
        "No fue posible calcular la huella",
        e
      );
    }
  }
}