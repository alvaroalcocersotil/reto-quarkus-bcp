package com.bcp.banking.transaction.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bcp.banking.transaction.client.AccountClient;
import com.bcp.banking.transaction.client.CustomerClient;
import com.bcp.banking.transaction.domain.Transaction;
import com.bcp.banking.transaction.domain.TransactionStatus;
import com.bcp.banking.transaction.dto.OperationRequest;
import com.bcp.banking.transaction.dto.ValidationResponse;
import com.bcp.banking.transaction.exception.SagaException;
import com.bcp.banking.transaction.observability.TransferMetrics;

import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

/**
 * Orquestador del Saga de transferencias (spec 03 §2).
 * SIN KAFKA: la notificación asíncrona usa Vert.x EventBus (transactions.completed/failed).
 * Fases: VALIDATION → DEBIT → CREDIT, con compensación del débito si el crédito falla.
 * Los llamados REST están FUERA de transacciones JTA; el estado se persiste por fase
 * mediante TransactionStateService.
 */
@ApplicationScoped
public class TransferSagaOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(TransferSagaOrchestrator.class);

    @Inject
    TransactionStateService state;
    @Inject
    @org.eclipse.microprofile.rest.client.inject.RestClient
    AccountClient accountClient;
    @Inject
    @org.eclipse.microprofile.rest.client.inject.RestClient
    CustomerClient customerClient;
    @Inject
    TransferMetrics metrics;
    @Inject
    EventBus eventBus;

    /** Ejecuta el Saga completo sobre una transacción PENDING ya persistida. */
    public Transaction execute(Transaction tx) {
        try {
            validationPhase(tx);
            debitPhase(tx);
            creditPhase(tx);
            state.markCompleted(tx.id);
            metrics.completed(tx.amount);
            publishCompleted(tx);
            LOG.info("Saga COMPLETED for tx={} amount={}", tx.id, tx.amount);
        } catch (SagaException e) {
            throw e;
        }
        return tx;
    }

    // ===== Fases =====

    private void validationPhase(Transaction tx) {
        state.markPhase(tx.id, TransactionStatus.VALIDATION, "VALIDATION");
        try {
            // las cuentas deben existir (404 → fallo de validación)
            accountClient.getBalance(tx.sourceAccountId);
            accountClient.getBalance(tx.targetAccountId);
            // el cliente dueño de la cuenta origen debe estar ACTIVE (422 si no)
            ValidationResponse v = customerClient.validate(tx.customerId);
            if (!v.eligibleForTransactions()) {
                throw new SagaException("customer-not-eligible", 422, "VALIDATION",
                        "Customer %s is not eligible for transactions".formatted(tx.customerId));
            }
        } catch (SagaException e) {
            state.markFailed(tx.id, "VALIDATION", e.getMessage());
            metrics.failed("VALIDATION");
            publishFailed(tx, "VALIDATION");
            throw e;
        } catch (WebApplicationException e) {
            String reason = "Validation failed: " + reason(e);
            state.markFailed(tx.id, "VALIDATION", reason);
            metrics.failed("VALIDATION");
            publishFailed(tx, "VALIDATION");
            throw new SagaException("validation-failed", remoteStatus(e), "VALIDATION", reason);
        }
    }

    private void debitPhase(Transaction tx) {
        try {
            state.markPhase(tx.id, TransactionStatus.DEBIT, "DEBIT");
            accountClient.debit(tx.sourceAccountId, new OperationRequest(tx.id, tx.amount));
        } catch (WebApplicationException e) {
            // fallo en DEBIT: no hay efectos previos, no se compensa
            state.markFailed(tx.id, "DEBIT", reason(e));
            metrics.failed("DEBIT");
            publishFailed(tx, "DEBIT");
            throw new SagaException("debit-failed", remoteStatus(e), "DEBIT", reason(e));
        }
    }

    private void creditPhase(Transaction tx) {
        try {
            state.markPhase(tx.id, TransactionStatus.CREDIT, "CREDIT");
            accountClient.credit(tx.targetAccountId, new OperationRequest(tx.id, tx.amount));
        } catch (RuntimeException e) {
            LOG.warn("CREDIT failed for tx={} — executing compensation", tx.id);
            String reason = (e instanceof WebApplicationException wae) ? reason(wae)
                    : "unexpected error: " + e.getMessage();
            state.markCompensationPending(tx.id, "CREDIT", reason);
            accountClient.compensateDebit(tx.sourceAccountId, new OperationRequest(tx.id, tx.amount));
            state.markFailed(tx.id, "CREDIT", reason);
            metrics.failed("CREDIT");
            publishFailed(tx, "CREDIT");
            throw new SagaException("credit-failed", 502, "CREDIT",
                    "Credit failed and source debit was compensated: " + reason);
        }
    }

    // ===== Helpers =====

    private void publishFailed(Transaction tx, String phase) {
        eventBus.publish("transactions.failed",
                "{\"transactionId\":\"%s\",\"status\":\"FAILED\",\"phase\":\"%s\"}".formatted(tx.id, phase));
    }

    private void publishCompleted(Transaction tx) {
        eventBus.publish("transactions.completed",
                "{\"transactionId\":\"%s\",\"status\":\"COMPLETED\",\"amount\":%s}"
                        .formatted(tx.id, tx.amount));
    }

    private static int remoteStatus(WebApplicationException e) {
        return e.getResponse() != null ? e.getResponse().getStatus() : 502;
    }

    private static String reason(WebApplicationException e) {
        return "HTTP %d from downstream".formatted(e.getResponse().getStatus());
    }
}
