package com.maycol.bancoias.infrastructure.out.r2dbc.adapter;

import com.maycol.bancoias.domain.model.Transfer;
import com.maycol.bancoias.domain.model.TransferCommand;
import com.maycol.bancoias.domain.model.TransferStatus;
import com.maycol.bancoias.domain.spi.ITransferProcessingPort;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    return validate(command)
      .then(
        findByReference(command.clientReference())
          .flatMap(existing ->
            sameRequest(existing, command)
              ? Mono.just(existing)
              : Mono.error(
              new IllegalArgumentException(
                "clientReference ya fue usada con datos diferentes"
              )
            )
          )
          .switchIfEmpty(
            createNew(command)
          )
      );
  }

  private Mono<Void> validate(TransferCommand command) {

    if (!"COP".equals(command.currency())) {
      return Mono.error(
        new IllegalArgumentException(
          "La moneda debe ser COP"
        )
      );
    }

    if (command.sourceAccount().equals(
      command.destinationAccount()
    )) {
      return Mono.error(
        new IllegalArgumentException(
          "La cuenta origen y destino deben ser diferentes"
        )
      );
    }

    if (command.amount().scale() > 2
      || command.amount().compareTo(BigDecimal.ZERO) <= 0) {

      return Mono.error(
        new IllegalArgumentException(
          "El monto debe ser mayor que cero y tener máximo dos decimales"
        )
      );
    }

    return Mono.empty();
  }

  private Mono<Transfer> createNew(
    TransferCommand command
  ) {

    UUID id = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();
    String fingerprint = createFingerprint(command);

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

  private Mono<Void> validateAccounts(
    TransferCommand command
  ) {

    return accountStatus(command.sourceAccount())
      .switchIfEmpty(
        Mono.error(
          new IllegalArgumentException(
            "La cuenta origen no existe"
          )
        )
      )
      .flatMap(status ->
        "ACTIVE".equals(status)
          ? Mono.empty()
          : Mono.error(
          new IllegalArgumentException(
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
              new IllegalArgumentException(
                "La cuenta destino no existe"
              )
            )
          )
          .flatMap(status ->
            "ACTIVE".equals(status)
              ? Mono.empty()
              : Mono.error(
              new IllegalArgumentException(
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
      .bind("number", number)
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
          new IllegalArgumentException(
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
          new IllegalArgumentException(
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
      .one();
  }

  private Mono<Transfer> findByReference(
    String reference
  ) {

    return db.sql(
        "SELECT * FROM transfers " +
          "WHERE client_reference=:ref"
      )
      .bind("ref", reference)
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
        createFingerprint(command)
      );
  }

  private String createFingerprint(
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

      return HexFormat.of().formatHex(
        MessageDigest
          .getInstance("SHA-256")
          .digest(
            payload.getBytes(
              StandardCharsets.UTF_8
            )
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