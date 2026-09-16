package com.maycol.bancoias.infrastructure.out.r2dbc.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Table("transfers")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TransferEntity {

  @Id
  private UUID id;

  @Column("client_reference")
  private String clientReference;

  @Column("request_fingerprint")
  private String requestFingerprint;

  @Column("source_account")
  private String sourceAccount;

  @Column("destination_account")
  private String destinationAccount;

  private BigDecimal amount;

  private String currency;

  private String status;

  @Column("processed_at")
  private OffsetDateTime processedAt;
}