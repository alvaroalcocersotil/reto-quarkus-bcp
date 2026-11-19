package com.bcp.banking.customer.observability;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Liveness básico (spec 01 §5). La readiness de DB la provee quarkus-smallrye-health.
 */
@Liveness
@ApplicationScoped
public class CustomerLivenessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("customer-service-liveness").up().build();
    }
}
