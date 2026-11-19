# Sistema de Transferencias Bancarias con Quarkus — BCP Challenge

3 microservicios Quarkus 3.15 (Java 21) que implementan una **Saga Orquestada** de transferencias bancarias:

| Servicio | Puerto | Rol en el Saga |
|---|---|---|
| `transaction-service` | **8080** | Orquestador del Saga (VALIDATION → DEBIT → CREDIT, con compensaciones) |
| `customer-service` | **8081** | Valida elegibilidad del cliente |
| `account-service` | **8082** | Débito / crédito / compensaciones (lock optimista `@Version`) |

- **24/24 tests verdes** (`mvn test`): customer 9, account 7, transaction 8.
- Comunicación síncrona: REST Clients con `@Retry`/`@Timeout`/`@CircuitBreaker`.
- Notificación asíncrona: **Vert.x EventBus** (`transactions.completed` / `transactions.failed`) — sin Kafka.
- Errores RFC 7807 (`application/problem+json`) con `correlationId`; trazabilidad vía header `X-Correlation-ID`.
- Seguridad: JWT RS256 firmado con llave compartida (`tools/GenKeys.java`); roles `ROLE_USER` / `ROLE_ADMIN`.

## 🚀 Levantar los 3 servicios (3 terminales, desde la raíz)

```powershell
# Terminal 1 — transaction-service (orquestador, puerto 8080)
mvn -pl transaction-service quarkus:dev

# Terminal 2 — customer-service (8081)
mvn -pl customer-service quarkus:dev

# Terminal 3 — account-service (8082)
mvn -pl account-service quarkus:dev
```

Cada uno arranca en `http://localhost:8080|8081|8082` con H2 en memoria y seeds precargados:
- Cliente ACTIVE: `11111111-1111-1111-1111-111111111111` (DNI 87654321)
- Cliente SUSPENDED: `22222222-2222-2222-2222-222222222222`
- Cuentas: `ACC-000001` (1000.00), `ACC-000002` (500.00), `ACC-000003` (BLOCKED)

## 🔑 Tokens JWT para la demo (Insomnia)

Los 3 servicios comparten la misma llave pública. Genera tokens de 24 horas con:

```powershell
java tools/GenDemoTokens.java
```

Imprime en consola `jwt_user` (ROLE_USER) y `jwt_admin` (ROLE_ADMIN); cópialos en el
environment **BCP Demo Local** de la colección de Insomnia (variables `jwt_user` / `jwt_admin`).

## 🧪 Importar la colección en Insomnia

1. Abrir **Insomnia** → `Create` → **Import** → **File**.
2. Seleccionar `docs/insomnia-collection-quarkus-transfers.json` (Insomnia Export v4).
3. La colección incluye el environment **BCP Demo Local** con `customer_url`, `account_url`,
   `transaction_url`, `jwt_user`, `jwt_admin` y `transfer_id`.
4. En **Manage Environments** puedes regenerar/pegar tokens nuevos sin tocar los requests.

> Los tokens incrustados en el JSON expiran en 24 h desde su generación.
> Si caducan: `java tools/GenDemoTokens.java` → copiar → reemplazar `jwt_user` / `jwt_admin` en el environment.

## 🎬 Guion de demo (colección Insomnia)

| # | Request | Esperado |
|---|---|---|
| 01 | `GET /q/health` y `/q/metrics` (3 servicios) | `200` UP + métricas `transfers.*` |
| 02 | Consultar / validar clientes seed | `200`; suspended → `422` |
| 02 | Crear cliente (jwt_admin) | `201` |
| 02 | Cliente con DNI duplicado `87654321` | `409` problem+json |
| 03 | Cuentas seed, `?customerId=`, `/balance` | `200` |
| 03 | `/internal/accounts/.../debit` con `jwt_user` | `403 Forbidden` (requiere ROLE_ADMIN) |
| 04 | Happy path (key `...a1`, 100.00 ACC1→ACC2) | `201 COMPLETED` |
| 04 | Replay con la MISMA key + payload | `200` (sin doble débito) |
| 04 | Misma key + monto distinto | `409 idempotency-conflict` |
| 04 | Saldo insuficiente / misma cuenta / ACC-000003 | `422` RFC 7807, sin compensación |
| 04 | Historial `GET /api/v1/transfers?status=COMPLETED` y detalle `/{id}` | `200` |

> **Nota:** el historial se consulta con `GET /api/v1/transfers` (lista + filtro `status`) y
> `GET /api/v1/transfers/{id}` — el transaction-service no expone
> `/api/v1/accounts/{número}/transactions`. Para el detalle por ID, copia el `id` de la
> respuesta del happy path (o el header `Location`) y pégalo en la variable `transfer_id`
> del environment.

## ✅ Ejecutar los tests

```powershell
mvn test                                  # los 3 módulos
mvn -pl transaction-service -am test      # solo transaction-service (+ dependientes)
```

## 🧰 Utilidades

| Comando | Descripción |
|---|---|
| `java tools/GenDemoTokens.java` | Tokens JWT demo (ROLE_USER / ROLE_ADMIN, 24 h) |
| `java tools/GenKeys.java <pub> <priv>` | Regenera el par RSA compartido (si se hace, redistribuir a los 3 servicios) |
| `mvn quarkus:dev -pl <servicio>` | Dev mode con live reload por servicio |
