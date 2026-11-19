---
name: quarkus-observability
description: Guía para implementar observabilidad en los microservicios Quarkus — métricas de negocio con MeterRegistry (contadores transfers.completed.total, transfers.failed.total y gauge transfers.amount.total), HealthChecks con @Liveness/@Readiness y propagación de X-Correlation-ID mediante ContainerRequestFilter y ClientRequestFilter con MDC. Activar al implementar /q/health, /q/metrics o trazabilidad.
---

# Skill: Observabilidad Quarkus (Micrometer + Health + Correlation ID)

## 1. Dependencias (`pom.xml`)

```xml
<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-micrometer-registry-prometheus</artifactId></dependency>
<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-smallrye-health</artifactId></dependency>
```

Configuración: `quarkus.micrometer.export.prometheus.path=/q/metrics` y `quarkus.smallrye-health.root-path=/q/health`.

## 2. Métricas de negocio (transaction-service)

```java
@ApplicationScoped
public class TransferMetrics {

    private final Counter completed;
    private final Counter failed;
    private final AtomicDouble totalAmount = new AtomicDouble(0.0);

    public TransferMetrics(MeterRegistry registry) {
        this.completed = Counter.builder("transfers.completed.total")
                .description("Transferencias completadas").register(registry);
        this.failed = registry.counter("transfers.failed.total"); // usar tags: counter("name","phase",phase)
        Gauge.builder("transfers.amount.total", totalAmount, AtomicDouble::get)
             .description("Monto acumulado transferido")
             .register(registry);
    }

    public void onCompleted(BigDecimal amount) { completed.increment(); totalAmount.addAndGet(amount.doubleValue()); }
    public void onFailed(String phase, BigDecimal amount) {
        io.micrometer.core.instrument.Counter.builder("transfers.failed.total")
            .tag("phase", phase).register(io.micrometer.core.instrument.Metrics.globalRegistry)
            .increment();
    }
}
```

Llamadas esperadas desde los consumidores `@ConsumeEvent("transactions.completed"|"transactions.failed")` — NO desde el recurso REST.

Verificación en `/q/metrics`:

```
transfers_completed_total 5.0
transfers_failed_total{phase="CREDIT"} 2.0
transfers_amount_total 4500.00
```

## 3. Health Checks

```java
@Liveness
@ApplicationScoped
public class ServiceLivenessCheck implements HealthCheck {
    @Override public HealthCheckResponse call() {
        return HealthCheckResponse.named("liveness-check").up().build();
    }
}

@Readiness
@ApplicationScoped
public class DatabaseReadinessCheck implements HealthCheck {
    @Inject EntityManager em; // o DataSource

    @Override public HealthCheckResponse call() {
        try {
            em.createNativeQuery("SELECT 1").getSingleResult();
            return HealthCheckResponse.named("database-ready").up()
                    .withData("db", "H2 in-memory").build();
        } catch (Exception e) {
            return HealthCheckResponse.named("database-ready").down()
                    .withData("error", e.getMessage()).build();
        }
    }
}
```

Endpoints: `/q/health`, `/q/health/live`, `/q/health/ready`.

## 4. Propagación de `X-Correlation-ID`

### 4.1 Servidor — `ContainerRequestFilter`

```java
@Provider
@Priority(Priorities.AUTHENTICATION)
public class CorrelationIdRequestFilter implements ContainerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    public void filter(ContainerRequestContext ctx) {
        String correlationId = ctx.getHeaderString(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
    }
}

// Opcional: devolver el header en la respuesta
@Provider
public class CorrelationIdResponseFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        String id = MDC.get(CorrelationIdRequestFilter.MDC_KEY);
        if (id != null) res.getHeaders().putSingle("X-Correlation-ID", id);
    }
}
```

### 4.2 Cliente REST — `ClientRequestFilter` (propagar hacia otros servicios)

```java
@Provider
public class CorrelationIdClientFilter implements ClientRequestFilter {
    @Override
    public void filter(ClientRequestContext ctx) {
        String id = MDC.get(CorrelationIdRequestFilter.MDC_KEY);
        if (id != null && ctx.getHeaderString("X-Correlation-ID") == null) {
            ctx.getHeaders().putSingle("X-Correlation-ID", id);
        }
        // Propagar también el JWT para llamadas service-to-service:
        String token = TokenHolder.currentToken(); // guardado en un filtro server
        if (token != null) ctx.getHeaders().putSingle("Authorization", "Bearer " + token);
    }
}
```

> En Quarkus también puede usarse `quarkus.rest-client.propagate-headers=X-Correlation-ID,Authorization` (header propagation nativa) como alternativa declarativa a 4.2.

### 4.3 Logging con MDC (`application.properties`)

```properties
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%X{correlationId}] %c{2} - %m%n
```

## 5. Checklist de observabilidad

- [ ] `/q/health/live` y `/q/health/ready` responden 200 en los 3 servicios.
- [ ] `transfers.completed.total` y `transfers.failed.total` incrementan tras transferencias reales.
- [ ] `transfers.amount.total` refleja la suma de montos completados.
- [ ] El `X-Correlation-ID` entrante se propaga a las llamadas REST salientes y a los logs.
- [ ] Los filtros están registrados como `@Provider` (o vía `@RegisterRestClient` config).
