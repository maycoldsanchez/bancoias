package com.maycol.bancoias.infrastructure.out.r2dbc.adapter;

import com.maycol.bancoias.domain.model.Transfer;
import com.maycol.bancoias.domain.model.TransferCommand;
import com.maycol.bancoias.domain.model.TransferStatus;
import com.maycol.bancoias.domain.spi.ITransferProcessingPort;
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

  public R2dbcTransferProcessingAdapter(DatabaseClient db, TransactionalOperator tx) {
    this.db = db;
    this.tx = tx;
  }

  @Override
  public Mono<Transfer> process(TransferCommand command) {

    if (!"COP".equals(command.currency())) {
      return Mono.error(new IllegalArgumentException("La moneda debe ser COP"));
    }

    if (command.sourceAccount().equals(command.destinationAccount())) {
      return Mono.error(new IllegalArgumentException(
        "La cuenta origen y destino deben ser diferentes"));
    }

    if (command.amount().scale() > 2
      || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
      return Mono.error(new IllegalArgumentException(
        "El monto debe ser mayor que cero y tener máximo dos decimales"));
    }

    return db.sql(
        "SELECT * FROM transfers WHERE client_reference = :ref")
      .bind("ref", command.clientReference())
      .map((row, metadata) -> new Transfer(
        row.get("id", UUID.class),
        row.get("client_reference", String.class),
        row.get("source_account", String.class),
        row.get("destination_account", String.class),
        row.get("amount", BigDecimal.class),
        row.get("currency", String.class),
        TransferStatus.valueOf(row.get("status", String.class)),
        row.get("processed_at", OffsetDateTime.class),
        row.get("request_fingerprint", String.class)
      ))
      .one()
      .flatMap(existing -> {

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

        String fingerprint;

        try {
          fingerprint = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
              .digest(payload.getBytes(StandardCharsets.UTF_8))
          );
        } catch (Exception e) {
          return Mono.error(e);
        }

        if (existing.fingerprint().equals(fingerprint)) {
          return Mono.just(existing);
        }

        return Mono.error(new IllegalArgumentException(
          "clientReference ya fue usada con datos diferentes"));
      })
      .switchIfEmpty(Mono.defer(() -> {

        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

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

        String fingerprint;

        try {
          fingerprint = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
              .digest(payload.getBytes(StandardCharsets.UTF_8))
          );
        } catch (Exception e) {
          return Mono.error(e);
        }

        return tx.transactional(

          db.sql(
              "SELECT status FROM accounts " +
                "WHERE account_number = :number")
            .bind("number", command.sourceAccount())
            .map((row, metadata) ->
              row.get("status", String.class))
            .one()
            .switchIfEmpty(Mono.error(
              new IllegalArgumentException(
                "La cuenta origen no existe")))
            .flatMap(status -> {

              if (!"ACTIVE".equals(status)) {
                return Mono.error(
                  new IllegalArgumentException(
                    "La cuenta origen no está activa"));
              }

              return Mono.empty();
            })

            .then(
              db.sql(
                  "SELECT status FROM accounts " +
                    "WHERE account_number = :number")
                .bind("number", command.destinationAccount())
                .map((row, metadata) ->
                  row.get("status", String.class))
                .one()
                .switchIfEmpty(Mono.error(
                  new IllegalArgumentException(
                    "La cuenta destino no existe")))
                .flatMap(status -> {

                  if (!"ACTIVE".equals(status)) {
                    return Mono.error(
                      new IllegalArgumentException(
                        "La cuenta destino no está activa"));
                  }

                  return Mono.empty();
                })
            )

            .then(
              db.sql(
                  "INSERT INTO transfers " +
                    "(id, client_reference, request_fingerprint, " +
                    "source_account, destination_account, amount, " +
                    "currency, status, processed_at) " +
                    "VALUES (:id,:ref,:fp,:source,:destination," +
                    ":amount,:currency,'PENDING',:processedAt)")
                .bind("id", id)
                .bind("ref", command.clientReference())
                .bind("fp", fingerprint)
                .bind("source", command.sourceAccount())
                .bind("destination", command.destinationAccount())
                .bind("amount", command.amount()
                  .setScale(2, RoundingMode.UNNECESSARY))
                .bind("currency", command.currency())
                .bind("processedAt", now)
                .fetch()
                .rowsUpdated()
            )

            .then(
              db.sql(
                  "UPDATE accounts SET balance = balance - :amount " +
                    "WHERE account_number = :number " +
                    "AND status = 'ACTIVE' " +
                    "AND currency = 'COP' " +
                    "AND balance >= :amount")
                .bind("amount", command.amount())
                .bind("number", command.sourceAccount())
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> {

                  if (rows != 1) {
                    return Mono.error(
                      new IllegalArgumentException(
                        "Saldo insuficiente"));
                  }

                  return Mono.empty();
                })
            )

            .then(
              db.sql(
                  "UPDATE accounts SET balance = balance + :amount " +
                    "WHERE account_number = :number " +
                    "AND status = 'ACTIVE' " +
                    "AND currency = 'COP'")
                .bind("amount", command.amount())
                .bind("number", command.destinationAccount())
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> {

                  if (rows != 1) {
                    return Mono.error(
                      new IllegalArgumentException(
                        "La cuenta destino no está activa"));
                  }

                  return Mono.empty();
                })
            )

            .then(
              db.sql(
                  "UPDATE transfers SET status='COMPLETED', " +
                    "processed_at=:processedAt WHERE id=:id")
                .bind("id", id)
                .bind("processedAt", now)
                .fetch()
                .rowsUpdated()
            )

            .then(
              db.sql(
                  "SELECT * FROM transfers WHERE id=:id")
                .bind("id", id)
                .map((row, metadata) -> new Transfer(
                  row.get("id", UUID.class),
                  row.get("client_reference", String.class),
                  row.get("source_account", String.class),
                  row.get("destination_account", String.class),
                  row.get("amount", BigDecimal.class),
                  row.get("currency", String.class),
                  TransferStatus.valueOf(
                    row.get("status", String.class)),
                  row.get("processed_at",
                    OffsetDateTime.class),
                  row.get("request_fingerprint",
                    String.class)
                ))
                .one()
            )
        );
      }));
  }

  @Override
  public Mono<Transfer> findById(UUID id) {

    return db.sql("SELECT * FROM transfers WHERE id=:id")
      .bind("id", id)
      .map((row, metadata) -> new Transfer(
        row.get("id", UUID.class),
        row.get("client_reference", String.class),
        row.get("source_account", String.class),
        row.get("destination_account", String.class),
        row.get("amount", BigDecimal.class),
        row.get("currency", String.class),
        TransferStatus.valueOf(row.get("status", String.class)),
        row.get("processed_at", OffsetDateTime.class),
        row.get("request_fingerprint", String.class)
      ))
      .one();
  }
}