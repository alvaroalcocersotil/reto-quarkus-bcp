package com.bcp.banking.account.observability;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Liveness básico (spec 02 §5). Readiness de DB la provee quarkus-smallrye-health.
 */
@Liveness
@ApplicationScoped
public class AccountLivenessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("account-service-liveness").up().build();
    }
}
