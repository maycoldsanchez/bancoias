package com.maycol.bancoias.infrastructure.out.r2dbc.repository;

import com.maycol.bancoias.infrastructure.out.r2dbc.entity.TransferEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface ITransferRepository
  extends ReactiveCrudRepository<TransferEntity, UUID> {

  Mono<TransferEntity> findByClientReference(String clientReference);

  @Modifying
  @Query("""
        INSERT INTO transfers (
            id,
            client_reference,
            request_fingerprint,
            source_account,
            destination_account,
            amount,
            currency,
            status,
            processed_at
        )
        VALUES (
            :id,
            :clientReference,
            :requestFingerprint,
            :sourceAccount,
            :destinationAccount,
            :amount,
            :currency,
            :status,
            :processedAt
        )
        """)
  Mono<Integer> insert(
    UUID id,
    String clientReference,
    String requestFingerprint,
    String sourceAccount,
    String destinationAccount,
    BigDecimal amount,
    String currency,
    String status,
    OffsetDateTime processedAt
  );

  @Modifying
  @Query("""
        UPDATE transfers
        SET status = 'COMPLETED',
            processed_at = :processedAt
        WHERE id = :id
        """)
  Mono<Integer> markCompleted(
    UUID id,
    OffsetDateTime processedAt
  );
}