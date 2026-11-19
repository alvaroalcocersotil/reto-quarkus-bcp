package com.bcp.banking.transaction.dto;

import com.bcp.banking.transaction.domain.Transaction;
import com.bcp.banking.transaction.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de transferencia (spec 03 §2).
 */
public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        String lastPhase,
        String failureReason,
        Instant createdAt) {

    public static TransferResponse from(Transaction t) {
        return new TransferResponse(t.id, t.sourceAccountId, t.targetAccountId, t.amount,
                t.currency, t.status, t.lastPhase, t.failureReason, t.createdAt);
    }
}
