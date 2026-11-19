package com.bcp.banking.transaction.service;

import java.math.BigDecimal;
import java.util.UUID;

import com.bcp.banking.transaction.domain.Transaction;
import com.bcp.banking.transaction.domain.TransactionRepository;
import com.bcp.banking.transaction.domain.TransactionStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Persistencia del estado del Saga. Métodos @Transactional separados del orquestador
 * porque los llamados REST externos NO deben estar dentro de una transacción JTA larga.
 */
@jakarta.enterprise.context.ApplicationScoped
public class TransactionStateService {

    @jakarta.inject.Inject
    TransactionRepository repository;

    @Transactional
    public Transaction create(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount,
                               UUID customerId, UUID idempotencyKey, String correlationId) {
        Transaction t = new Transaction();
        t.sourceAccountId = sourceAccountId;
        t.targetAccountId = targetAccountId;
        t.amount = amount;
        t.customerId = customerId;
        t.idempotencyKey = idempotencyKey;
        t.correlationId = correlationId;
        t.status = TransactionStatus.PENDING;
        repository.persist(t);
        return t;
    }

    @Transactional
    public void markPhase(UUID id, TransactionStatus status, String phase) {
        Transaction t = repository.findByIdOptional(id).orElseThrow();
        t.status = status;
        t.lastPhase = phase;
    }

    @Transactional
    public void markCompleted(UUID id) {
        Transaction t = repository.findById(id);
        t.status = TransactionStatus.COMPLETED;
    }

    @Transactional
    public void markCompensationPending(UUID id, String phase, String reason) {
        Transaction t = repository.findById(id);
        t.status = TransactionStatus.COMPENSATION_PENDING;
        t.lastPhase = phase;
        t.failureReason = reason;
    }

    @Transactional
    public void markFailed(UUID id, String phase, String reason) {
        Transaction t = repository.findById(id);
        t.status = TransactionStatus.FAILED;
        t.lastPhase = phase;
        t.failureReason = reason;
    }

    @Transactional
    public void markFailedWithCompensation(UUID id, String phase, String reason) {
        Transaction t = repository.findById(id);
        t.status = TransactionStatus.COMPENSATION_PENDING;
        t.lastPhase = phase;
        t.failureReason = reason;
    }
}
