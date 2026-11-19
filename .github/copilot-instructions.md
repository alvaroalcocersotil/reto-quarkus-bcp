# Reglas Globales Obligatorias — Sistema de Transferencias Bancarias (Quarkus)

> **Alcance:** Estas reglas aplican SIEMPRE (always-on) a los 3 microservicios del challenge:
> `customer-service`, `account-service`, `transaction-service`.
> Ante conflicto entre este documento y una spec en `specs/`, prevalece la spec.

---

## 🚫 Restricción CRÍTICA #1: PROHIBIDO Apache Kafka

- **NUNCA** generar código, dependencias (`quarkus-messaging-kafka`, `kafka-clients`), configuración (`kafka.bootstrap.servers`), Docker Compose con Kafka, ni mención de brokers externos en ningún archivo.
- El messaging asíncrono se resuelve **exclusivamente** con **Vert.x EventBus** (`io.vertx.mutiny.core.eventbus.EventBus`, `@ConsumeEvent`) dentro del límite de cada microservicio.
- La comunicación entre microservicios es **exclusivamente síncrona** vía **MicroProfile REST Client reactivo** (`quarkus-rest-client-reactive-jackson`) + **SmallRye Fault Tolerance** (`@Retry`, `@Timeout`, `@CircuitBreaker`, `@Fallback`).
- Si el usuario pide Kafka, redirige amablemente a esta regla y propón el equivalente con EventBus/REST.

---

## 1. Lenguaje y Runtime

| Aspecto | Regla |
|---|---|
| JDK | **Java 21** (LTS). Solo features estables; `--enable-preview` NO permitido. |
| Framework | **Quarkus 3.x** (namespace `jakarta.*`, NUNCA `javax.*`). |
| Records | DTOs, payloads de eventos y value objects SIEMPRE como `record` inmutables. |
| Pattern Matching | `switch` con pattern matching / sealed interfaces donde aplique (ej. resultados de la Saga). |
| Lombok | **PROHIBIDO**. Records + Panache lo hacen innecesario. |

```java
// ✅ ESTÁNDAR: DTO como record
public record TransferRequest(
    @NotBlank String sourceAccount,
    @NotBlank String targetAccount,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank String currency) {}
```

---

## 2. API REST

- Motor: **RESTEasy Reactive** (`quarkus-rest` + `quarkus-rest-jackson`), rutas bajo `/api/v1/...` con `jakarta.ws.rs.*`.
- Recursos **delgados**: sin lógica de negocio; delegan en servicios.
- Validación de entrada con `quarkus-hibernate-validator` (`@Valid`, `@NotBlank`, `@NotNull`, `@DecimalMin`).
- Errores: **Problem Details RFC 7807** (`application/problem+json`) vía `ExceptionMapper<T>` global (`@Provider`), siempre incluyendo `correlationId`.

```json
{
  "type": "https://bcp-challenge.com/errors/insufficient-funds",
  "title": "Insufficient funds",
  "status": 422,
  "detail": "Account ACC-000001 has balance 50.00, required 100.00",
  "instance": "/api/v1/transfers",
  "correlationId": "c0a80101-8f2e-4b7a-9c1d-3e5f6a7b8c9d"
}
```

| Excepción | HTTP |
|---|---|
| Entidad inexistente | `404` |
| `ConstraintViolationException` / payload inválido | `400` |
| Estado de negocio inválido (cuenta inactiva, misma cuenta, moneda inválida) | `422` |
| Saldo insuficiente | `422` con `type=.../insufficient-funds` |
| `OptimisticLockException` | `409` (el cliente puede reintentar) |
| Conflicto de idempotencia (misma key, payload distinto) | `409` |
| `TimeoutException` / CircuitBreaker abierto | `503` |
| No controlada | `500` (sin filtrar stacktrace al cliente) |

---

## 3. Persistencia

- ORM: **Hibernate ORM with Panache** (`quarkus-hibernate-orm-panache`). Patrón Repository con `PanacheRepositoryBase<Entity, Id>`.
- Transacciones: `@Transactional` en la capa de **servicio** (nunca en recursos REST).
- BD por defecto: **H2 en memoria** (`quarkus-jdbc-h2`) para dev/test. El perfil `%prod` para **PostgreSQL** (`quarkus-jdbc-postgresql`) debe existir **comentado** en `application.properties`.
- Concurrencia en `Account`:
  - **Optimistic Locking obligatorio**: campo `@Version` en la entidad `Account`.
  - Exclusión estricta puntual: **bloqueo pesimista** `LockModeType.PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`).
  - `OptimisticLockException` → HTTP `409`. Nunca silenciarla.

## 4. Saga de transferencia (reglas de orquestación)

- **Saga Orquestada** con `transaction-service` como orquestador (ver `specs/00-architecture-and-saga.md`).
- Pasos remotos con REST Client reactivo; **cada paso tiene su compensación** (`debit` ↔ `compensate-debit`, `credit` ↔ `compensate-credit`).
- Ante fallo irreversible: ejecutar compensaciones en orden inverso y emitir evento `transactions.failed`.
- Ante éxito: emitir evento `transactions.completed`.
- La transferencia se modela como agregado `Transaction` con estados: `INITIATED → DEBITED → COMPLETED` o `INITIATED/DEBITED → COMPENSATING → FAILED`.
- **Idempotencia**: header `Idempotency-Key` (UUID) obligatorio en `POST /api/v1/transfers`; repetir la clave devuelve el resultado original (HTTP `200` en replays, `201` la primera vez) sin duplicar efectos.

---

## 5. Eventos internos (Vert.x EventBus)

- Publicación: `eventBus.publish("transactions.completed", payload)` / `"transactions.failed"` — payloads como **records** serializables a JSON.
- Consumo: métodos con `@ConsumeEvent("transactions.completed")` (y `.failed`) en `transaction-service` (auditoría + métricas) y `account-service` (notificaciones/métricas locales).
- Los consumidores son post-procesamiento: NUNCA llaman a otros microservicios de forma síncrona en el hot path.
- Errores en consumidores se registran y no revierten la transferencia.

---

## 6. Seguridad

- **SmallRye JWT** (`quarkus-smallrye-jwt`): todas las rutas de negocio con `@RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})`.
- Endpoints internos entre microservicios (`/internal/**`): requieren rol `ROLE_ADMIN` (service-to-service); no se exponen en el OpenAPI público.
- Llaves RSA locales (`privateKey.pem`/`publicKey.pem`) solo para dev/test; JWKS comentado para `%prod`.
- CORS habilitado (`quarkus.http.cors=true`).
- PROHIBIDO loguear PII (documentos, tokens JWT completos).

---

## 7. Observabilidad (obligatoria en los 3 servicios)

| Capacidad | Herramienta | Endpoint |
|---|---|---|
| Health | SmallRye Health (`@Liveness`, `@Readiness`) | `/q/health`, `/q/health/live`, `/q/health/ready` |
| Métricas | Micrometer + Prometheus | `/q/metrics` |
| Contadores | `transfers.completed.total`, `transfers.failed.total` (tag `phase`) | Counter |
| Gauge | `transfers.amount.total` (monto acumulado transferido) | Gauge / FunctionCounter |
| Trazabilidad | `X-Correlation-ID` en header; propagado por `ContainerRequestFilter` (server) y `ClientRequestFilter` (REST Client) y guardado en **MDC** (`correlationId`). | — |

---

## 8. Pruebas y calidad

- Tests con **`@QuarkusTest` + RestAssured**. Cobertura mínima exigida:
  1. Transferencia exitosa (`201`).
  2. Saldo insuficiente (`422`).
  3. Cuenta inexistente (`404`).
  4. Cuenta inactiva/bloqueada (`422`).
  5. Transferencia a la misma cuenta (`422`).
  6. Monto ≤ 0 (`400`).
  7. Sin JWT / rol insuficiente (`401`/`403`).
  8. Idempotencia: replay con misma `Idempotency-Key` no duplica efectos.
- Auditoría anti-Kafka: verificar que **ningún** `pom.xml` o propiedad contenga `kafka`.

---

## 9. Convenciones de proyecto (Maven multi-módulo)

```
reto/
├── pom.xml                     (padre)
├── customer-service/pom.xml
├── account-service/pom.xml
├── transaction-service/pom.xml
├── specs/                      (Single Source of Truth)
├── .github/copilot-instructions.md
├── .github/skills/
└── .github/agents/
```

- Un módulo Maven por microservicio; puertos dev: `customer-service` **8081**, `account-service` **8082**, `transaction-service` **8080**.
- `application.properties` con perfiles: `%dev`/`%test` (H2), `%prod` comentado (PostgreSQL + JWKS).
- IDs de entidades: `UUID`; números de cuenta formato `ACC-XXXXXX`.
- Identificadores de código en inglés; comentarios y specs pueden estar en español.

