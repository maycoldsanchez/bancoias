package com.maycol.bancoias.infrastructure.configuration;

import  com.maycol.bancoias.application.handler.*;
import  com.maycol.bancoias.domain.api.ITransferServicePort;
import  com.maycol.bancoias.domain.spi.ITransferProcessingPort;
import  com.maycol.bancoias.domain.usecase.TransferUseCase;
import  com.maycol.bancoias.infrastructure.out.r2dbc.adapter.R2dbcTransferProcessingAdapter;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.*;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration public class BeanConfiguration {

  @Bean
  TransactionalOperator transactionalOperator(ConnectionFactory cf) {
    return TransactionalOperator.create(new R2dbcTransactionManager(cf));
  }

  @Bean
  ITransferProcessingPort transferProcessingPort(DatabaseClient db, TransactionalOperator tx) {
    return new R2dbcTransferProcessingAdapter(db, tx);
  }

  @Bean
  ITransferServicePort transferServicePort(ITransferProcessingPort p) {
    return new TransferUseCase(p);
  }

  @Bean
  ITransferHandler transferHandler(ITransferServicePort p) {
    return new TransferHandler(p);
  }
}
