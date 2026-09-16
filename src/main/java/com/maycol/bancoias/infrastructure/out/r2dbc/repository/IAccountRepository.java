package com.maycol.bancoias.infrastructure.out.r2dbc.repository;

import com.maycol.bancoias.infrastructure.out.r2dbc.entity.AccountEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface IAccountRepository
  extends ReactiveCrudRepository<AccountEntity, UUID> {

  Mono<AccountEntity> findByAccountNumber(String accountNumber);

  @Modifying
  @Query("""
        UPDATE accounts
        SET balance = balance - :amount
        WHERE account_number = :accountNumber
          AND status = 'ACTIVE'
          AND currency = 'COP'
          AND balance >= :amount
        """)
  Mono<Integer> debit(
    String accountNumber,
    BigDecimal amount
  );

  @Modifying
  @Query("""
        UPDATE accounts
        SET balance = balance + :amount
        WHERE account_number = :accountNumber
          AND status = 'ACTIVE'
          AND currency = 'COP'
        """)
  Mono<Integer> credit(
    String accountNumber,
    BigDecimal amount
  );
}