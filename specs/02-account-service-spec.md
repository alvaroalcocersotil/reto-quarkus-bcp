# 02 — Especificación: `account-service`

> Cuentas corrientes, saldos, débito/crédito atómico y control de concurrencia. **Estado:** APROBADA. **Puerto dev:** `8082`. **BD:** H2 `accountsdb` (dev/test), PostgreSQL comentado en `%prod`.

---

## 1. Modelo de datos

### Entidad `Account`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `UUID` | PK, `GenerationType.UUID` |
| `accountNumber` | `String` | `unique`, `@NotBlank`, formato `ACC-\d{6}` |
| `customerId` | `UUID` | NOT NULL (referencia lógica a customer-service) |
| `balance` | `BigDecimal(19,2)` | NOT NULL, `>= 0` (invariante en débito) |
| `currency` | `enum CurrencyCode { PEN, USD }` | `@Enumerated(STRING)`, NOT NULL |
| `status` | `enum AccountStatus { ACTIVE, BLOCKED, CLOSED }` | `@Enumerated(STRING)`, NOT NULL |
| `version` | `Long` | **`@Version` — OPTIMISTIC LOCKING obligatorio** |
| `createdAt` / `updatedAt` | `Instant` | timestamps |

Repositorio: `AccountRepository implements PanacheRepositoryBase<Account, UUID>`
métodos clave: `Optional<Account> findByAccountNumber(String)`,
`List<Account> findByCustomerId(UUID)`.

### DTOs (records)

```java
public record AccountResponse(UUID id, String accountNumber, UUID customerId,
        BigDecimal balance, CurrencyCode currency, AccountStatus status,
        Long version) {}

public record OperationRequest(@NotNull @DecimalMin("0.01") BigDecimal amount,
        String currency, String correlationId) {}

public record OperationResponse(String accountNumber, BigDecimal newBalance,
        Long version, boolean success) {}
```

## 2. Endpoints públicos

`@RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})`

| Método | Ruta | Descripción | Éxito | Errores |
|---|---|---|---|---|
| `GET` | `/api/v1/accounts/{accountNumber}` | Cuenta por número | `200` | `404` |
| `GET` | `/api/v1/customers/{customerId}/accounts` | Cuentas de un cliente | `200` (lista, puede ser vacía) | — |
| `GET` | `/api/v1/accounts` | Listar (ADMIN, paginado) | `200` | — |
| `POST` | `/api/v1/accounts` | Crear cuenta (ADMIN; saldo inicial ≥ 0) | `201` | `400`, `409` |
| `PATCH` | `/api/v1/accounts/{accountNumber}/status` | Activar/Bloquear (ADMIN) | `200` | `404`, `400` |

## 3. Endpoints internos (Saga) — `/api/v1/internal/**`

> No se exponen en OpenAPI público. Requieren `ROLE_ADMIN` (service-to-service) + `X-Correlation-ID`.

| Método | Ruta | Efecto | Éxito | Errores |
|---|---|---|---|---|
| `POST` | `/api/v1/internal/accounts/{accountNumber}/debit` | Resta `amount` del saldo | `200` + `OperationResponse` | `404`, `409` (OptimisticLock), `422` (saldo insuficiente / no activa), `503` |
| `POST` | `/api/v1/internal/accounts/{accountNumber}/credit` | Suma `amount` al saldo | `200` | `404`, `409`, `422` (no activa), `503` |
| `POST` | `/api/v1/internal/accounts/{accountNumber}/compensate-debit` | **Recredita** al origen (compensación Saga) | `200` | `404`, `409`, `503` |
| `POST` | `/api/v1/internal/accounts/{accountNumber}/compensate-credit` | Re-debita (aborto posterior al crédito) | `200` | `404`, `409`, `422`, `503` |

### Reglas de débito/crédito (OBLIGATORIAS)

1. Cada operación corre en su **propia `@Transactional`** (transacción local corta; nunca transacciones distribuidas).
2. Débito: si `balance - amount < 0` → `422` `insufficient-funds`, **sin** modificar la entidad.
3. Débito/crédito sobre cuenta con estado distinto de `ACTIVE` → `422` `account-not-active`.
4. La modificación de `balance` pasa **siempre** por la entidad con `@Version` → modificación concurrente produce `OptimisticLockException` → HTTP `409` (retryable por el orquestador).
5. Lock pesimista opcional en débito: `find("accountNumber", n).withLock(LockModeType.PESSIMISTIC_WRITE)`; si se bloquean varias cuentas, ordenar por `accountNumber` ascendente.
6. `compensate-debit` NO valida saldo (siempre incrementa); exige cuenta existente.
7. Idempotencia de operaciones internas: si el mismo `correlationId` ya aplicó la misma operación sobre la cuenta, devolver `200` sin re-aplicar.

## 4. Consumidor EventBus (notificaciones internas — SIN Kafka)

```java
@ApplicationScoped
public class AccountNotificationConsumer {

    @ConsumeEvent("transactions.completed")
    public void onTransfersCompleted(TransfersCompletedEvent event) {
        Log.infof("Account notified: transfer %s completed (%s -> %s)",
                  event.transactionId(), event.sourceAccount(), event.targetAccount());
        // post-procesamiento: notificaciones / métricas locales. Nunca REST síncrono crítico.
    }
}
```

## 5. Escenarios de concurrencia esperados

| Escenario | Resultado |
|---|---|
| Débito exitoso | `200`, `balance` disminuye, `version` +1 |
| Débito con saldo insuficiente | `422` `insufficient-funds`, `version` sin cambio |
| Débito cuenta BLOCKED/CLOSED | `422` `account-not-active` |
| Débito cuenta inexistente | `404` |
| Dos débitos simultáneos misma cuenta | Uno `200`, otro `409` (optimistic) o serializados (pessimistic) |

## 6. Datos de prueba (seed dev/test, `import.sql`)

| accountNumber | customerId | balance | currency | status |
|---|---|---|---|---|
| `ACC-000001` | `11111111-1111-1111-1111-111111111111` | 1000.00 | PEN | ACTIVE |
| `ACC-000002` | `11111111-1111-1111-1111-111111111111` | 500.00 | PEN | ACTIVE |
| `ACC-000003` | `22222222-2222-2222-2222-222222222222` | 250.00 | PEN | BLOCKED |

## 7. Observabilidad

- `/q/health` con `@Readiness` (ping BD). `/q/metrics` con contador opcional `accounts.operations.total{type=debit|credit|compensate}` y propagación `X-Correlation-ID`.

