package com.maycol.bancoias.infrastructure.configuration;

import com.maycol.bancoias.application.handler.ITransferHandler;
import com.maycol.bancoias.application.handler.TransferHandler;
import com.maycol.bancoias.domain.api.ITransferServicePort;
import com.maycol.bancoias.domain.spi.ITransferProcessingPort;
import com.maycol.bancoias.domain.usecase.TransferUseCase;
import com.maycol.bancoias.infrastructure.out.r2dbc.adapter.R2dbcTransferProcessingAdapter;
import com.maycol.bancoias.infrastructure.out.r2dbc.mapper.TransferEntityMapper;
import com.maycol.bancoias.infrastructure.out.r2dbc.repository.IAccountRepository;
import com.maycol.bancoias.infrastructure.out.r2dbc.repository.ITransferRepository;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
public class BeanConfiguration {

  @Bean
  TransactionalOperator transactionalOperator(ConnectionFactory connectionFactory) {
    return TransactionalOperator.create(
      new R2dbcTransactionManager(connectionFactory)
    );
  }

  @Bean
  ITransferProcessingPort transferProcessingPort(
    ITransferRepository transferRepository,
    IAccountRepository accountRepository,
    TransferEntityMapper transferEntityMapper,
    TransactionalOperator transactionalOperator) {

    return new R2dbcTransferProcessingAdapter(
      transferRepository,
      accountRepository,
      transferEntityMapper,
      transactionalOperator
    );
  }

  @Bean
  ITransferServicePort transferServicePort(
    ITransferProcessingPort transferProcessingPort) {

    return new TransferUseCase(transferProcessingPort);
  }

  @Bean
  ITransferHandler transferHandler(
    ITransferServicePort transferServicePort) {

    return new TransferHandler(transferServicePort);
  }
}