package com.maycol.bancoias.infrastructure.out.r2dbc.adapter;

import com.maycol.bancoias.domain.model.*;
import com.maycol.bancoias.domain.spi.ITransferProcessingPort;
import com.maycol.bancoias.infrastructure.exception.*;
import io.r2dbc.spi.Row;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

public class R2dbcTransferProcessingAdapter implements ITransferProcessingPort {

  private final DatabaseClient db;
  private final TransactionalOperator tx;

  public R2dbcTransferProcessingAdapter(
    DatabaseClient db,
    TransactionalOperator tx
  ) {
    this.db = db;
    this.tx = tx;
  }

  @Override
  public Mono<Transfer> process(TransferCommand command) {

    return Mono.defer(() ->
        validate(command)
          .then(
            findByReference(
              command.clientReference()
            )
              .flatMap(existing ->
                sameRequest(
                  existing,
                  command
                )
                  ? Mono.just(existing)
                  : Mono.error(
                  new BusinessException(
                    "IDEMPOTENCY_CONFLICT",
                    "clientReference ya fue usada con datos diferentes"
                  )
                )
              )
              .switchIfEmpty(
                createNew(command)
              )
          )
      )
      .onErrorResume(
        DataIntegrityViolationException.class,
        e ->
          findByReference(
            command.clientReference()
          )
            .switchIfEmpty(
              Mono.error(e)
            )
            .flatMap(existing ->
              sameRequest(
                existing,
                command
              )
                ? Mono.just(existing)
                : Mono.error(
                new BusinessException(
                  "IDEMPOTENCY_CONFLICT",
                  "clientReference ya fue usada con datos diferentes"
                )
              )
            )
      );
  }

  private Mono<Transfer> createNew(
    TransferCommand command
  ) {

    UUID id = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();
    String fingerprint = fingerprint(command);

    return tx.transactional(
      validateAccounts(command)
        .then(
          insert(
            id,
            command,
            fingerprint,
            now
          )
        )
        .then(
          debit(command)
        )
        .then(
          credit(command)
        )
        .then(
          markCompleted(id, now)
        )
        .then(
          findById(id)
        )
    );
  }

  private Mono<Void> validate(
    TransferCommand command
  ) {

    if (!"COP".equals(command.currency())) {
      return Mono.error(
        new BusinessException(
          "INVALID_CURRENCY",
          "La moneda debe ser COP"
        )
      );
    }

    if (command.sourceAccount().equals(
      command.destinationAccount()
    )) {
      return Mono.error(
        new BusinessException(
          "SAME_ACCOUNT",
          "La cuenta origen y destino deben ser diferentes"
        )
      );
    }

    if (command.amount().scale() > 2
      || command.amount().compareTo(
      BigDecimal.ZERO
    ) <= 0) {

      return Mono.error(
        new BusinessException(
          "INVALID_AMOUNT",
          "El monto debe ser mayor que cero y tener máximo dos decimales"
        )
      );
    }

    return Mono.empty();
  }

  private Mono<Void> validateAccounts(
    TransferCommand command
  ) {

    return accountStatus(
      command.sourceAccount()
    )
      .switchIfEmpty(
        Mono.error(
          new BusinessException(
            "SOURCE_ACCOUNT_NOT_FOUND",
            "La cuenta origen no existe"
          )
        )
      )
      .flatMap(status ->
        "ACTIVE".equals(status)
          ? Mono.empty()
          : Mono.error(
          new BusinessException(
            "SOURCE_ACCOUNT_INACTIVE",
            "La cuenta origen no está activa"
          )
        )
      )
      .then(
        accountStatus(
          command.destinationAccount()
        )
          .switchIfEmpty(
            Mono.error(
              new BusinessException(
                "DESTINATION_ACCOUNT_NOT_FOUND",
                "La cuenta destino no existe"
              )
            )
          )
          .flatMap(status ->
            "ACTIVE".equals(status)
              ? Mono.empty()
              : Mono.error(
              new BusinessException(
                "DESTINATION_ACCOUNT_INACTIVE",
                "La cuenta destino no está activa"
              )
            )
          )
      );
  }

  private Mono<String> accountStatus(
    String number
  ) {

    return db.sql(
        "SELECT status FROM accounts " +
          "WHERE account_number = :number"
      )
      .bind(
        "number",
        number
      )
      .map((row, metadata) ->
        row.get(
          "status",
          String.class
        )
      )
      .one();
  }

  private Mono<Void> insert(
    UUID id,
    TransferCommand command,
    String fingerprint,
    OffsetDateTime now
  ) {

    return db.sql(
        "INSERT INTO transfers " +
          "(id, client_reference, request_fingerprint, " +
          "source_account, destination_account, amount, " +
          "currency, status, processed_at) " +
          "VALUES (:id,:ref,:fp,:source,:destination," +
          ":amount,:currency,'PENDING',:processedAt)"
      )
      .bind("id", id)
      .bind(
        "ref",
        command.clientReference()
      )
      .bind(
        "fp",
        fingerprint
      )
      .bind(
        "source",
        command.sourceAccount()
      )
      .bind(
        "destination",
        command.destinationAccount()
      )
      .bind(
        "amount",
        command.amount()
          .setScale(
            2,
            RoundingMode.UNNECESSARY
          )
      )
      .bind(
        "currency",
        command.currency()
      )
      .bind(
        "processedAt",
        now
      )
      .fetch()
      .rowsUpdated()
      .then();
  }

  private Mono<Void> debit(
    TransferCommand command
  ) {

    return db.sql(
        "UPDATE accounts SET " +
          "balance = balance - :amount " +
          "WHERE account_number = :number " +
          "AND status = 'ACTIVE' " +
          "AND currency = 'COP' " +
          "AND balance >= :amount"
      )
      .bind(
        "amount",
        command.amount()
      )
      .bind(
        "number",
        command.sourceAccount()
      )
      .fetch()
      .rowsUpdated()
      .flatMap(rows ->
        rows == 1
          ? Mono.empty()
          : Mono.error(
          new BusinessException(
            "INSUFFICIENT_BALANCE",
            "Saldo insuficiente"
          )
        )
      )
      .then();
  }

  private Mono<Void> credit(
    TransferCommand command
  ) {

    return db.sql(
        "UPDATE accounts SET " +
          "balance = balance + :amount " +
          "WHERE account_number = :number " +
          "AND status = 'ACTIVE' " +
          "AND currency = 'COP'"
      )
      .bind(
        "amount",
        command.amount()
      )
      .bind(
        "number",
        command.destinationAccount()
      )
      .fetch()
      .rowsUpdated()
      .flatMap(rows ->
        rows == 1
          ? Mono.empty()
          : Mono.error(
          new BusinessException(
            "DESTINATION_ACCOUNT_INACTIVE",
            "La cuenta destino no está activa"
          )
        )
      )
      .then();
  }

  private Mono<Void> markCompleted(
    UUID id,
    OffsetDateTime now
  ) {

    return db.sql(
        "UPDATE transfers SET " +
          "status='COMPLETED', " +
          "processed_at=:processedAt " +
          "WHERE id=:id"
      )
      .bind("id", id)
      .bind(
        "processedAt",
        now
      )
      .fetch()
      .rowsUpdated()
      .then();
  }

  @Override
  public Mono<Transfer> findById(
    UUID id
  ) {

    return db.sql(
        "SELECT * FROM transfers " +
          "WHERE id=:id"
      )
      .bind("id", id)
      .map(this::map)
      .one()
      .switchIfEmpty(
        Mono.error(
          new TransferNotFoundException()
        )
      );
  }

  private Mono<Transfer> findByReference(
    String ref
  ) {

    return db.sql(
        "SELECT * FROM transfers " +
          "WHERE client_reference=:ref"
      )
      .bind("ref", ref)
      .map(this::map)
      .one();
  }

  private Transfer map(
    Row row,
    Object ignored
  ) {

    return new Transfer(
      row.get("id", UUID.class),
      row.get(
        "client_reference",
        String.class
      ),
      row.get(
        "source_account",
        String.class
      ),
      row.get(
        "destination_account",
        String.class
      ),
      row.get(
        "amount",
        BigDecimal.class
      ),
      row.get(
        "currency",
        String.class
      ),
      TransferStatus.valueOf(
        row.get(
          "status",
          String.class
        )
      ),
      row.get(
        "processed_at",
        OffsetDateTime.class
      ),
      row.get(
        "request_fingerprint",
        String.class
      )
    );
  }

  private boolean sameRequest(
    Transfer transfer,
    TransferCommand command
  ) {

    return transfer.fingerprint()
      .equals(
        fingerprint(command)
      );
  }

  private String fingerprint(
    TransferCommand command
  ) {

    try {

      String payload = String.join(
        "|",
        command.clientReference(),
        command.sourceAccount(),
        command.destinationAccount(),
        command.amount()
          .setScale(
            2,
            RoundingMode.UNNECESSARY
          )
          .toPlainString(),
        command.currency()
      );

      return HexFormat.of()
        .formatHex(
          MessageDigest
            .getInstance("SHA-256")
            .digest(
              payload.getBytes(
                StandardCharsets.UTF_8
              )
            )
        );

    } catch (Exception e) {
      throw new IllegalStateException("No fue posible calcular la huella", e);
    }
  }
}