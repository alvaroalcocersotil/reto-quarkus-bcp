# 00 — Arquitectura General y Patrón Saga Orquestado

> **Single Source of Truth** del flujo de transferencia. Todo el código debe ajustarse a este documento.
> **Estado:** APROBADA para implementación. **Versión:** 1.0

---

## 1. Visión de alto nivel

```
┌─────────────────────┐        ┌─────────────────────┐        ┌──────────────────────────┐
│  customer-service   │        │  account-service    │        │  transaction-service     │
│  :8081              │        │  :8082              │        │  :8080 (orquestador)     │
│─────────────────────│        │─────────────────────│        │──────────────────────────│
│ • Clientes          │◄───────│ • Cuentas (ownerId) │◄───────│ • POST /api/v1/transfers │
│ • Validación estado │  REST  │ • Débito/Crédito    │  REST  │ • Saga orquestada        │
│                     │ Client │ • Compensaciones    │ Client │ • Historial / auditoría  │
│ [H2/PG]             │        │ • @Version (optim.) │        │ • EventBus consumers     │
│                     │        │ • Pessimistic lock  │        │ • Micrometer counters    │
└─────────────────────┘        └─────────────────────┘        └──────────────────────────┘
        ▲                              ▲                              ▲
        └───────────── JWT (SmallRye) + X-Correlation-ID + RFC 7807 ──┘
```

- **Síncrono (REST):** `transaction-service` llama a `customer-service` y `account-service` vía MicroProfile REST Client reactivo con `@Retry`, `@Timeout`, `@CircuitBreaker`.
- **Asíncrono (interno, SIN Kafka):** Vert.x EventBus dentro de cada servicio para `transactions.completed` / `transactions.failed` (auditoría, métricas, notificaciones).
- **Persistencia:** H2 en memoria (dev/test). `%prod` → PostgreSQL (comentado en `application.properties`).

## 2. Componentes y puertos

| Servicio | Puerto dev | BD | Responsabilidad |
|---|---|---|---|
| `transaction-service` | 8080 | H2 (`transactionsdb`) | Orquestador Saga, historial, auditoría, métricas de negocio |
| `customer-service` | 8081 | H2 (`customersdb`) | Clientes y validación de estado |
| `account-service` | 8082 | H2 (`accountsdb`) | Cuentas, saldos, débito/crédito atómico, concurrencia |

## 3. Modelo de eventos (equivalencia Kafka → EventBus)

| Evento original | Implementación | Canal | Publicador | Consumidores |
|---|---|---|---|---|
| `transactions.completed` | `eventBus.publish("transactions.completed", TransfersCompletedEvent)` | Vert.x EventBus | `transaction-service` (tras Saga OK) | `transaction-service`: auditoría + contador; `account-service`: notificación/métrica |
| `transactions.failed` | `eventBus.publish("transactions.failed", TransfersFailedEvent)` | Vert.x EventBus | `transaction-service` (tras fallo/compensación) | ídem |

Payloads como **Java records**:

```java
public record TransfersCompletedEvent(UUID transactionId, String sourceAccount,
        String targetAccount, BigDecimal amount, String currency,
        Instant completedAt, String correlationId) {}

public record TransfersFailedEvent(UUID transactionId, String reason,
        FailurePhase phase, /* VALIDATION | DEBIT | CREDIT */
        String correlationId, Instant failedAt) {}
```

## 4. Diagrama de secuencia — Transferencia exitosa (Happy Path)

```
Cliente        transaction-service(:8080)      customer-service(:8081)      account-service(:8082)
  │  POST /api/v1/transfers        │                              │                            │
  │  Idempotency-Key: <UUID>       │                              │                            │
  │───────────────────────────────►│                              │                            │
  │                                │ (1) Validar JWT (ROLE_USER)  │                            │
  │                                │ (2) Idempotencia: ¿existe tx │                            │
  │                                │     con esa key? ── no ─────►│                            │
  │                                │ (3) GET /api/v1/customers/{id}/validate                   │
  │                                │─────────────────────────────►│ (cliente ACTIVE)           │
  │                                │◄────────────── 200 OK ───────│                            │
  │                                │ (4) GET /api/v1/accounts/{src} + {dst}                    │
  │                                │──────────────────────────────────────────────────────────►│
  │                                │◄──────────────────────────── 200 OK (ACTIVE, moneda OK) ──│
  │                                │     tx.status = INITIATED (persistida antes de llamar)    │
  │                                │ (5) POST /internal/accounts/{src}/debit                   │
  │                                │──────────────────────────────────────────────────────────►│
  │                                │     [@Version: UPDATE con WHERE version = n]              │
  │                                │◄──────────────────────────── 200 OK (nuevo balance) ──────│
  │                                │     tx.status = DEBITED                                   │
  │                                │ (6) POST /internal/accounts/{dst}/credit                  │
  │                                │──────────────────────────────────────────────────────────►│
  │                                │◄──────────────────────────── 200 OK ──────────────────────│
  │                                │     tx.status = COMPLETED                                 │
  │                                │ (7) eventBus.publish("transactions.completed", evt)       │
  │                                │     ──► auditoría, transfers.completed.total++,           │
  │                                │         gauge transfers.amount.total += amount            │
  │◄───────────── 201 Created ─────│                                                           │
  │  {transactionId, COMPLETED}    │                                                           │
```

## 5. Diagrama de secuencia — Fallo en el crédito + COMPENSACIÓN

```
Cliente        transaction-service            account-service
  │  POST /transfers                │                            │
  │────────────────────────────────►│                            │
  │                                 │ (1..5) idem happy path     │
  │                                 │ POST /internal/debit  ────►│ ✅ 200 (débito aplicado)
  │                                 │     tx.status = DEBITED    │
  │                                 │ POST /internal/credit ────►│ ❌ 409 (OptimisticLock) o
  │                                 │                            │    422 (cuenta inactiva) o
  │                                 │                            │    503 (Timeout / CB open)
  │                                 │ (6) SAGA: compensación     │
  │                                 │ POST /internal/compensate-debit {src, amount}
  │                                 │───────────────────────────►│ ✅ 200 (recredito al origen)
  │                                 │     tx.status = COMPENSATING → FAILED
  │                                 │ (7) eventBus.publish("transactions.failed", evt)
  │                                 │     ──► auditoría, transfers.failed.total++ (tag phase)
  │◄───────────── 422 / 503 ────────│   (Problem Details RFC 7807 + correlationId)
```

### Reglas de compensación (OBLIGATORIAS)

1. **Fase VALIDATION** (cuentas inactivas, misma cuenta, monto ≤ 0, cliente inexistente/inactivo): falla **antes** de cualquier débito → sin compensación, `transactions.failed` con `phase=VALIDATION`.
2. **Fase DEBIT falla**: sin efectos → sin compensación, `phase=DEBIT`.
3. **Fase CREDIT falla** (débito ya aplicado): compensación obligatoria `compensate-debit` → `phase=CREDIT`. Si la compensación también falla → reintentos (`@Retry` max 3) y estado `COMPENSATION_PENDING` visible en auditoría (alerta operativa).
4. `OptimisticLockException` remota (HTTP 409) → el orquestador puede reintentar **una vez** la operación; si persiste, falla sin compensar si el débito nunca llegó a aplicar.
5. Toda llamada remota: `@Timeout(2s)`, `@Retry(maxRetries=2, delay=200ms, jitter)`, `@CircuitBreaker(requestVolumeThreshold=8, failureRatio=0.5, delay=5s)`.

## 6. Concurrencia (por qué `@Version`)

- Dos transferencias simultáneas sobre la misma cuenta origen: Hibernate incrementa `version` al hacer `UPDATE`; si otra transacción modificó la fila, la segunda recibe `OptimisticLockException` → HTTP `409` → el orquestador puede reintentar releyendo el saldo.
- Débito/crédito en `account-service` corren en su propia `@Transactional` (transacción local corta); la Saga NO mantiene transacciones distribuidas.
- Si se usan locks pesimistas (`PESSIMISTIC_WRITE`, `SELECT ... FOR UPDATE`) en el débito, ordenar siempre los locks por `accountNumber` ascendente para evitar deadlocks.

## 7. Idempotencia

- `POST /api/v1/transfers` exige header `Idempotency-Key: <UUID>`.
- `transaction-service` persiste `(idempotencyKey → transactionId, requestHash)`:
  - Misma key + mismo payload → resultado original (`200`).
  - Misma key + payload distinto → `409 Conflict`.
  - Key en curso (`INITIATED`/`DEBITED`) → `409`.

## 8. Contratos entre servicios (resumen)

| Origen → Destino | Operación | Método y ruta | Éxito | Fallos |
|---|---|---|---|---|
| transaction → customer | Validar cliente activo | `GET /api/v1/customers/{id}/validate` | 200 | 404, 422 |
| transaction → account | Leer cuenta | `GET /api/v1/accounts/{accountNumber}` | 200 | 404 |
| transaction → account | Débito | `POST /api/v1/internal/accounts/{n}/debit` | 200 | 404, 409, 422, 503 |
| transaction → account | Crédito | `POST /api/v1/internal/accounts/{n}/credit` | 200 | 404, 409, 422, 503 |
| transaction → account | Compensar débito | `POST /api/v1/internal/accounts/{n}/compensate-debit` | 200 | 404, 409, 503 |

Los endpoints `/internal/**` NO se publican en el OpenAPI público del gateway.

## 9. Observabilidad transversal

- `X-Correlation-ID`: generado en el edge si no existe; propagado en headers del REST Client y en MDC (`%X{correlationId}` en el pattern de log).
- Métricas (`/q/metrics`): `transfers.completed.total` (Counter), `transfers.failed.total` (Counter con tag `phase`), `transfers.amount.total` (Gauge/FunctionCounter).
- Health: `@Liveness` (app up) y `@Readiness` (conectividad BD) en los 3 servicios.

## 10. Definición de Done (arquitectural)

1. Los 3 servicios arrancan con `mvnw quarkus:dev` sin Docker ni brokers externos.
2. Flujo completo end-to-end y compensación ejecutan según specs 01–03.
3. `/q/health` y `/q/metrics` expuestos en los 3 servicios.
4. Cero referencias a Kafka en el repositorio (verificado por QA).


