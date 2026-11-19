# 01 — Especificación: `customer-service`

> Gestión y validación de clientes. **Estado:** APROBADA. **Puerto dev:** `8081`. **BD:** H2 `customersdb` (dev/test), PostgreSQL comentado en `%prod`.

---

## 1. Modelo de datos

### Entidad `Customer` (Panache)

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `UUID` | PK, `GenerationType.UUID` |
| `firstName` | `String` | `@NotBlank`, máx 100 |
| `lastName` | `String` | `@NotBlank`, máx 100 |
| `documentType` | `enum DocumentType { DNI, CE, PASSPORT, RUC }` | `@Enumerated(STRING)`, NOT NULL |
| `documentNumber` | `String` | `unique`, `@NotBlank`, máx 20 |
| `email` | `String` | `unique`, `@Email`, `@NotBlank` |
| `phoneNumber` | `String` | máx 15, opcional |
| `status` | `enum CustomerStatus { ACTIVE, SUSPENDED, CLOSED }` | `@Enumerated(STRING)`, NOT NULL, default `ACTIVE` |
| `createdAt` | `Instant` | `@CreationTimestamp` |
| `updatedAt` | `Instant` | `@UpdateTimestamp` |

Repositorio: `CustomerRepository implements PanacheRepositoryBase<Customer, UUID>`
método clave: `Optional<Customer> findByDocument(String type, String number)`.

### DTOs (records)

```java
public record CustomerResponse(UUID id, String firstName, String lastName,
        DocumentType documentType, String documentNumber, String email,
        String phoneNumber, CustomerStatus status, Instant createdAt) {}

public record CreateCustomerRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 20) String documentNumber,
        @NotBlank @Email String email,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$") String phoneNumber) {}
```

> Nota: `@Version` NO es obligatorio en `Customer` (sin concurrencia crítica de saldo),
> pero se permite como buena práctica.

## 2. Endpoints

Base path: `/api/v1/customers` · `@RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})` en rutas públicas.

| Método | Ruta | Roles | Descripción | Éxito |
|---|---|---|---|---|
| `GET` | `/api/v1/customers/{id}` | USER, ADMIN | Obtener cliente por UUID | `200` + `CustomerResponse` |
| `GET` | `/api/v1/customers?documentType=&documentNumber=` | USER, ADMIN | Buscar por documento | `200` (lista, 0..1) |
| `GET` | `/api/v1/customers` | USER, ADMIN | Listar paginado (`page`, `size` ≤ 100) | `200` |
| `POST` | `/api/v1/customers` | ADMIN | Crear cliente | `201` + `Location` |
| `PATCH` | `/api/v1/customers/{id}/status` | ADMIN | Cambiar estado (`ACTIVE`/`SUSPENDED`/`CLOSED`) | `200` |
| `GET` | `/api/v1/customers/{id}/validate` | USER, ADMIN | Validación ligera para orquestador (ver §4) | `200` |

## 3. Códigos HTTP y errores (RFC 7807)

| Situación | HTTP | `type` (suffix) |
|---|---|---|
| Cliente encontrado | `200` | — |
| Cliente inexistente | `404` | `customer-not-found` |
| Payload inválido (Bean Validation) | `400` | `validation-error` |
| Documento o email duplicado | `409` | `duplicate-customer` |
| Cliente SUSPENDED/CLOSED (en validate) | `422` | `customer-not-active` |
| Sin token / token inválido | `401` | — |
| Rol insuficiente | `403` | — |
| Error interno | `500` | `internal-error` |

## 4. Reglas de validación de negocio

1. `documentType` + `documentNumber` **únicos** en conjunto → si duplicado, `409`.
2. `email` único (case-insensitive) → `409`.
3. `email` con formato RFC simplificado; teléfono opcional `^\+?[0-9]{7,15}$`.
4. No se permite crear clientes con estado distinto de `ACTIVE`.
5. `/api/v1/customers/{id}/validate` responde:

```json
{ "customerId": "...", "status": "ACTIVE", "eligibleForTransactions": true }
```

- `eligibleForTransactions = (status == ACTIVE)`. Si no es elegible → HTTP `422` con `type=customer-not-active` (el orquestador lo trata como fallo de fase VALIDATION).

## 5. Seguridad

- SmallRye JWT: `mp.jwt.verify.publickey.location=publicKey.pem` (dev/test), JWKS comentado para `%prod`.
- Solo `ROLE_ADMIN` puede crear/modificar clientes. `ROLE_USER` solo lectura.
- CORS: `quarkus.http.cors=true`.

## 6. Datos de prueba (seed dev/test, `import.sql`)

| id (fijo) | document | status |
|---|---|---|
| `11111111-1111-1111-1111-111111111111` | DNI 87654321 | ACTIVE |
| `22222222-2222-2222-2222-222222222222` | DNI 12345678 | SUSPENDED |

## 7. Observabilidad

- `/q/health` con `@Readiness` verificando `SELECT 1` contra H2.
- `/q/metrics` expuesto; propagación de `X-Correlation-ID` (ver skill `quarkus-observability`).
