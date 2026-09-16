package com.maycol.bancoias.infrastructure.out.r2dbc.mapper;

import com.maycol.bancoias.domain.model.Transfer;
import com.maycol.bancoias.domain.model.TransferCommand;
import com.maycol.bancoias.domain.model.TransferStatus;
import com.maycol.bancoias.infrastructure.out.r2dbc.entity.TransferEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-16T15:45:15-0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 22.0.2 (Oracle Corporation)"
)
@Component
public class TransferEntityMapperImpl implements TransferEntityMapper {

    @Override
    public Transfer toTransfer(TransferEntity transferEntity) {
        if ( transferEntity == null ) {
            return null;
        }

        String fingerprint = null;
        UUID id = null;
        String clientReference = null;
        String sourceAccount = null;
        String destinationAccount = null;
        BigDecimal amount = null;
        String currency = null;
        TransferStatus status = null;
        OffsetDateTime processedAt = null;

        fingerprint = transferEntity.getRequestFingerprint();
        id = transferEntity.getId();
        clientReference = transferEntity.getClientReference();
        sourceAccount = transferEntity.getSourceAccount();
        destinationAccount = transferEntity.getDestinationAccount();
        amount = transferEntity.getAmount();
        currency = transferEntity.getCurrency();
        if ( transferEntity.getStatus() != null ) {
            status = Enum.valueOf( TransferStatus.class, transferEntity.getStatus() );
        }
        processedAt = transferEntity.getProcessedAt();

        Transfer transfer = new Transfer( id, clientReference, sourceAccount, destinationAccount, amount, currency, status, processedAt, fingerprint );

        return transfer;
    }

    @Override
    public TransferEntity toEntity(UUID id, TransferCommand command, String fingerprint, OffsetDateTime processedAt) {
        if ( id == null && command == null && fingerprint == null && processedAt == null ) {
            return null;
        }

        TransferEntity transferEntity = new TransferEntity();

        if ( command != null ) {
            transferEntity.setClientReference( command.clientReference() );
            transferEntity.setSourceAccount( command.sourceAccount() );
            transferEntity.setDestinationAccount( command.destinationAccount() );
            transferEntity.setAmount( command.amount() );
            transferEntity.setCurrency( command.currency() );
        }
        transferEntity.setId( id );
        transferEntity.setRequestFingerprint( fingerprint );
        transferEntity.setProcessedAt( processedAt );
        transferEntity.setStatus( "PENDING" );

        return transferEntity;
    }
}
