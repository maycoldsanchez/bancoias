package com.maycol.bancoias.infrastructure.out.r2dbc.repository;

import com.maycol.bancoias.infrastructure.out.r2dbc.entity.TransferEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ITransferRepository
  extends ReactiveCrudRepository<TransferEntity, UUID> {

  Mono<TransferEntity> findByClientReference(String clientReference);

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