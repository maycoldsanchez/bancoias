package com.maycol.bancoias.infrastructure.out.r2dbc.mapper;

import com.maycol.bancoias.domain.model.Transfer;
import com.maycol.bancoias.domain.model.TransferCommand;
import com.maycol.bancoias.infrastructure.out.r2dbc.entity.TransferEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.OffsetDateTime;
import java.util.UUID;

@Mapper(
  componentModel = "spring",
  unmappedTargetPolicy = ReportingPolicy.IGNORE,
  unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface TransferEntityMapper {

  @Mapping(target = "fingerprint", source = "requestFingerprint")
  Transfer toTransfer(TransferEntity transferEntity);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "clientReference", source = "command.clientReference")
  @Mapping(target = "requestFingerprint", source = "fingerprint")
  @Mapping(target = "sourceAccount", source = "command.sourceAccount")
  @Mapping(target = "destinationAccount", source = "command.destinationAccount")
  @Mapping(target = "amount", source = "command.amount")
  @Mapping(target = "currency", source = "command.currency")
  @Mapping(target = "status", constant = "PENDING")
  @Mapping(target = "processedAt", source = "processedAt")
  TransferEntity toEntity(
    UUID id,
    TransferCommand command,
    String fingerprint,
    OffsetDateTime processedAt
  );
}