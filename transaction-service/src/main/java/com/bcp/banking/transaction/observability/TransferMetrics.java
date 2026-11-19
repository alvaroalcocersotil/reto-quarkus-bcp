package com.bcp.banking.transaction.observability;

import java.util.concurrent.atomic.DoubleAdder;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Métricas de negocio (spec 03 §4):
 * - Counter transfers.completed.total
 * - Counter transfers.failed.total{phase}
 * - Gauge   transfers.amount.total
 */
@ApplicationScoped
public class TransferMetrics {

    private final io.micrometer.core.instrument.Counter completed;
    private final io.micrometer.core.instrument.Counter failedValidation;
    private final io.micrometer.core.instrument.Counter failedDebit;
    private final io.micrometer.core.instrument.Counter failedCredit;
    private final DoubleAdder completedAmount = new DoubleAdder();

    @jakarta.inject.Inject
    public TransferMetrics(MeterRegistry registry) {
        this.completed = registry.counter("transfers.completed.total");
        this.failedValidation = registry.counter("transfers.failed.total", "phase", "VALIDATION");
        this.failedDebit = registry.counter("transfers.failed.total", "phase", "DEBIT");
        this.failedCredit = registry.counter("transfers.failed.total", "phase", "CREDIT");
        registry.gauge("transfers.amount.total", completedAmount, DoubleAdder::doubleValue);
    }

    public void completed(java.math.BigDecimal amount) {
        completed.increment();
        completedAmount.add(amount.doubleValue());
    }

    public void failed(String phase) {
        switch (phase) {
            case "VALIDATION" -> failedValidation.increment();
            case "DEBIT" -> failedDebit.increment();
            case "CREDIT" -> failedCredit.increment();
            default -> { /* fase desconocida */ }
        }
    }
}
