package com.bcp.banking.account.event;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.eventbus.Message;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Consumidor Vert.x EventBus de eventos del orquestador (spec 02 §3).
 * SIN KAFKA: los eventos transactions.completed / transactions.failed viajan
 * por el EventBus local (in-process). Su uso aquí es trazabilidad/auditoría;
 * la compensación real la dispara transaction-service vía /internal/accounts/*.
 */
@ApplicationScoped
public class TransactionEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionEventConsumer.class);

    public record TransactionEvent(String transactionId, String status, String phase, String reason) {
    }

    @ConsumeEvent("transactions.completed")
    @Blocking
    public void onCompleted(TransactionEvent event) {
        LOG.info("[EB] transactions.completed tx={} — débito/crédito consolidados", event.transactionId());
    }

    @ConsumeEvent("transactions.failed")
    @Blocking
    public void onFailed(TransactionEvent event) {
        LOG.warn("[EB] transactions.failed tx={} phase={} — compensaciones ejecutadas por el orquestador",
                event.transactionId(), event.phase());
    }
}
