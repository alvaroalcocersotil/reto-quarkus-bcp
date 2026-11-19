# 03 — Especificación: `transaction-service`

> Orquestador de transferencias (Saga), historial y auditoría. **Estado:** APROBADA. **Puerto dev:** `8080`. **BD:** H2 `transactionsdb` (dev/test), PostgreSQL comentado en `%prod`.

---

## 1. Modelo de datos

### Entidad `Transaction`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `UUID` | PK |
| `idempotencyKey` | `UUID` | `unique`, NOT NULL (header `Idempotency-Key`) |
| `sourceAccount` / `targetAccount` | `String` | NOT NULL |
| `amount` | `BigDecimal(19,2)` | NOT NULL, `> 0` |
| `currency` | `String` | NOT NULL (debe coincidir en ambas cuentas) |
| `status` | `enum TransactionStatus` | NOT NULL |
| `failureReason` | `String` | nullable |
| `failurePhase` | `enum FailurePhase { VALIDATION, DEBIT, CREDIT }` | nullable |
| `correlationId` | `String` | NOT NULL |
| `createdAt` / `completedAt` | `Instant` | timestamps |

`enum TransactionStatus { INITIATED, DEBITED, COMPLETED, COMPENSATING, FAILED, COMPENSATION_PENDING }`

Transiciones válidas: `INITIATED → DEBITED → COMPLETED` · `INITIATED|DEBITED → COMPENSATING → FAILED` · `COMPENSATING → COMPENSATION_PENDING` (compensación fallida tras reintentos).

### DTOs (records)

```java
public record TransferRequest(
        @NotBlank String sourceAccount,
        @NotBlank String targetAccount,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String currency) {}

public record TransferResponse(UUID transactionId, TransactionStatus status,
        String sourceAccount, String targetAccount, BigDecimal amount,
        String currency, Instant createdAt, Instant completedAt,
        String failureReason, FailurePhase failurePhase) {}
```

## 2. Endpoint principal: `POST /api/v1/transfers`

- Roles: `@RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})`.
- Headers: `Idempotency-Key` (UUID, **obligatorio**), `X-Correlation-ID` (generado si falta).

### Validaciones (en orden; falla corta con RFC 7807)

| # | Regla | HTTP | type (suffix) |
|---|---|---|---|
| 1 | Payload válido (Bean Validation) | `400` | `validation-error` |
| 2 | `Idempotency-Key` presente y UUID válido | `400` | `missing-idempotency-key` |
| 3 | `amount > 0`, escala ≤ 2 | `400` | `validation-error` |
| 4 | `sourceAccount != targetAccount` | `422` | `same-account-transfer` |
| 5 | Idempotencia: replay (misma key + mismo payload) | `200` + resultado original | — |
| 6 | Idempotencia: misma key + payload distinto | `409` | `idempotency-conflict` |
| 7 | Cliente propietario activo (REST → customer-service) | `422`/`404` | `customer-not-active` |
| 8 | Cuentas existen y están `ACTIVE` (REST → account-service) | `404`/`422` | `account-not-found` / `account-not-active` |
| 9 | Monedas coinciden con `currency` | `422` | `currency-mismatch` |
| 10 | Saldo suficiente (valida account-service en el débito) | `422` | `insufficient-funds` |

> El estado `INITIATED` se persiste **antes** de las llamadas remotas (reglas 7–10) para trazabilidad de la Saga.

### Ejemplo

```http
POST /api/v1/transfers HTTP/1.1
Authorization: Bearer <jwt ROLE_USER>
Idempotency-Key: 7e2f8a1c-3b4d-4e5f-9a0b-1c2d3e4f5a6b
X-Correlation-ID: corr-abc-123
Content-Type: application/json

{"sourceAccount": "ACC-000001", "targetAccount": "ACC-000002",
 "amount": 150.00, "currency": "PEN"}
```

`201 Created` → `TransferResponse{status=COMPLETED}`. Replay con la misma `Idempotency-Key` → `200 OK` con la respuesta original, **sin duplicar efectos**.

## 3. Endpoints de consulta

| Método | Ruta | Roles | Descripción | Éxito |
|---|---|---|---|---|
| `GET` | `/api/v1/accounts/{accountNumber}/transactions` | USER, ADMIN | Historial paginado (`page`, `size` ≤ 100) donde la cuenta sea origen **o** destino | `200` (lista, posiblemente vacía) |
| `GET` | `/api/v1/transactions/{id}` | USER, ADMIN | Transacción por UUID | `200` / `404` |

## 4. Orquestación de la Saga (resumen ejecutable)

1. Validaciones locales + idempotencia → persistir `Transaction(INITIATED)`.
2. `GET customer validate` (REST + Fault Tolerance). Fallo → `FAILED` `phase=VALIDATION`.
3. `GET` cuentas origen/destino → validar activas y moneda. Fallo → `FAILED` `phase=VALIDATION`.
4. `POST debit(src)` → OK: `status=DEBITED`. Fallo irreversible → `FAILED` `phase=DEBIT` (sin compensación).
5. `POST credit(dst)` → OK: `status=COMPLETED` + `eventBus.publish("transactions.completed", evt)`.
   Fallo → `status=COMPENSATING` → `POST compensate-debit(src)` → OK: `FAILED`; si falla tras `@Retry(3)`: `COMPENSATION_PENDING`.
6. Métricas: `transfers.completed.total`++, `transfers.failed.total{phase}`++, gauge `transfers.amount.total` += amount (solo COMPLETED).

### Fault Tolerance obligatorio en los clientes REST

```java
@RegisterRestClient(configKey = "account-service")
public interface AccountClient {
    @POST @Path("/api/v1/internal/accounts/{accountNumber}/debit")
    OperationResponse debit(@PathParam("accountNumber") String n, OperationRequest req);
    @POST @Path("/api/v1/internal/accounts/{accountNumber}/credit")
    OperationResponse credit(@PathParam("accountNumber") String n, OperationRequest req);
    @POST @Path("/api/v1/internal/accounts/{accountNumber}/compensate-debit")
    OperationResponse compensateDebit(@PathParam("accountNumber") String n, OperationRequest req);
}
// Configuración en el bean cliente:
@Retry(maxRetries = 2, delay = 200, delayUnit = ChronoUnit.MILLIS, jitter = 100)
@Timeout(value = 2, unit = ChronoUnit.SECONDS)
@CircuitBreaker(requestVolumeThreshold = 8, failureRatio = 0.5, delay = 5, delayUnit = ChronoUnit.SECONDS)
```

## 5. Consumidores `@ConsumeEvent` (equivalente a consumidores Kafka)

```java
@ApplicationScoped
public class TransactionEventConsumers {

    @Inject MeterRegistry meterRegistry;

    @ConsumeEvent("transactions.completed")
    public void onCompleted(TransfersCompletedEvent event) {
        persistAudit("COMPLETED", event.transactionId(), event.correlationId());
        meterRegistry.counter("transfers.completed.total").increment();
        // gauge transfers.amount.total acumulado con event.amount()
    }

    @ConsumeEvent("transactions.failed")
    public void onFailed(TransfersFailedEvent event) {
        persistAudit("FAILED:" + event.phase(), event.transactionId(), event.correlationId());
        meterRegistry.counter("transfers.failed.total", "phase", event.phase().name()).increment();
    }
}
```

Reglas: los consumidores NO lanzan excepciones hacia el orquestador; fallos se loguean con `correlationId`. Payloads: records JSON-serializables.

## 6. Configuración REST Client (`application.properties`)

```properties
quarkus.rest-client.account-service.url=http://localhost:8082
quarkus.rest-client.customer-service.url=http://localhost:8081
```

## 7. Datos de prueba y auditoría

- Sin filas iniciales obligatorias; los tests crean transacciones vía API.
- Tabla recomendada `transaction_audit(id, transaction_id, event_type, payload_json, created_at)` para el consumidor de auditoría.

## 8. Observabilidad

- `/q/health`: readiness con BD (y opcionalmente estado de CircuitBreakers).
- `/q/metrics`: `transfers.completed.total`, `transfers.failed.total`, gauge `transfers.amount.total`.
- MDC `correlationId` en todos los logs de la Saga.

