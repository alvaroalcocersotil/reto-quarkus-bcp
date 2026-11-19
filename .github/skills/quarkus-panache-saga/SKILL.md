---
name: quarkus-panache-saga
description: Guía paso a paso y ejemplos few-shot para implementar transacciones @Transactional, Optimistic Locking con @Version en entidades Panache y compensaciones del patrón Saga orquestado sin Kafka (REST reactivo + Vert.x EventBus). Activar al implementar account-service, transaction-service o cualquier lógica de débito/crédito/compensación.
---

# Skill: Quarkus Panache + Saga sin Kafka

## Cuándo usar esta skill

- Implementar entidades y repositorios Panache con concurrencia.
- Implementar débito/crédito/compensación de la Saga de transferencias.
- Manejar `OptimisticLockException` y compensaciones inversas.

## Paso 1 — Entidad con `@Version` (obligatorio en Account)

```java
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(unique = true, nullable = false)
    public String accountNumber;          // ACC-000001

    @Column(nullable = false)
    public UUID customerId;

    @Column(precision = 19, scale = 2, nullable = false)
    public BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AccountStatus status;          // ACTIVE, BLOCKED, CLOSED

    @Version
    public Long version;                  // OPTIMISTIC LOCKING — evita carreras en transferencias
}
```

## Paso 2 — Repository con `PanacheRepositoryBase`

```java
@ApplicationScoped
public class AccountRepository implements PanacheRepositoryBase<Account, UUID> {

    public Optional<Account> findByAccountNumber(String accountNumber) {
        return find("accountNumber", accountNumber).firstResultOptional();
    }

    public Optional<Account> findByAccountNumberForUpdate(String accountNumber) {
        return find("accountNumber", accountNumber)
                .withLock(LockModeType.PESSIMISTIC_WRITE)   // SELECT ... FOR UPDATE (opcional en débito)
                .firstResultOptional();
    }

    public List<Account> findByCustomerId(UUID customerId) {
        return list("customerId", customerId);
    }
}
```

## Paso 3 — Débito/crédito atómico con `@Transactional` (patrón few-shot)

```java
@ApplicationScoped
public class AccountService {

    @Inject AccountRepository repository;

    @Transactional // en el SERVICIO, nunca en el recurso REST
    public OperationResponse debit(String accountNumber, BigDecimal amount) {
        Account account = repository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NotFoundException("Account " + accountNumber + " not found"));

        if (account.status != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("account-not-active",
                    "Account " + accountNumber + " is " + account.status);
        }
        if (account.balance.subtract(amount).signum() < 0) {
            throw new BusinessRuleException("insufficient-funds",
                    "Account %s has balance %s, required %s"
                        .formatted(accountNumber, account.balance, amount));
        }

        account.balance = account.balance.subtract(amount);
        // Hibernate: UPDATE accounts SET balance=?, version=version+1 WHERE id=? AND version=?
        // Si otra tx modificó la fila → OptimisticLockException (→ HTTP 409 en el mapper)
        repository.flush(); // fuerza el UPDATE (y el optimistic check) dentro de esta tx
        return new OperationResponse(accountNumber, account.balance, account.version, true);
    }

    @Transactional
    public OperationResponse credit(String accountNumber, BigDecimal amount) {
        Account account = repository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NotFoundException("Account " + accountNumber + " not found"));
        if (account.status != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("account-not-active", "Account " + accountNumber + " is not active");
        }
        account.balance = account.balance.add(amount);
        repository.flush();
        return new OperationResponse(accountNumber, account.balance, account.version, true);
    }

    /** Compensación Saga: recreditar al origen. NO valida saldo (siempre incrementa). */
    @Transactional
    public OperationResponse compensateDebit(String accountNumber, BigDecimal amount) {
        Account account = repository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NotFoundException("Account " + accountNumber + " not found"));
        account.balance = account.balance.add(amount);
        repository.flush();
        return new OperationResponse(accountNumber, account.balance, account.version, true);
    }
}
```

## Paso 4 — Saga orquestada sin Kafka (transaction-service)

```java
@ApplicationScoped
public class TransferOrchestrator {

    @Inject AccountClient accountClient;   // interface @RegisterRestClient
    @Inject EventBus eventBus;             // io.vertx.mutiny.core.eventbus.EventBus — SIN Kafka

    public TransactionResult execute(TransferCommand cmd) {
        // 1) Validaciones locales + persistir Transaction(INITIATED)
        // 2) Débito remoto (Fault Tolerance: @Retry/@Timeout/@CircuitBreaker en el cliente)
        accountClient.debit(cmd.sourceAccount(), req(cmd));
        tx.markDebited();
        try {
            // 3) Crédito
            accountClient.credit(cmd.targetAccount(), req(cmd));
            tx.complete();
            eventBus.publish("transactions.completed", TransfersCompletedEvent.of(tx));
            return TransactionResult.completed(tx);
        } catch (RemoteStepException e) {
            // 4) COMPENSACIÓN en orden inverso (solo porque el débito YA se aplicó)
            tx.markCompensating();
            accountClient.compensateDebit(cmd.sourceAccount(), req(cmd));
            tx.fail(e.reason(), FailurePhase.CREDIT);
            eventBus.publish("transactions.failed", TransfersFailedEvent.of(tx, e));
            return TransactionResult.failed(tx);
        }
    }
}
```

## Paso 5 — Consumidores post-procesamiento (equivalente a consumidores Kafka)

```java
@ApplicationScoped
public class TransactionEventConsumers {

    @ConsumeEvent("transactions.completed")
    public void onCompleted(TransfersCompletedEvent event) { /* auditoría + métricas */ }

    @ConsumeEvent("transactions.failed")
    public void onFailed(TransfersFailedEvent event) { /* auditoría + métricas con tag phase */ }
}
```

## Reglas de oro

1. `@Transactional` en servicios, NUNCA en recursos REST.
2. `@Version` en toda entidad con saldo/monto modificable.
3. Cada paso remoto del Saga tiene compensación inversa; se ejecutan en orden inverso.
4. Compensar **solo** lo que ya se aplicó (si el débito falló, no hay nada que compensar).
5. El Saga NO abre transacciones distribuidas: cada llamada remota es una transacción local corta.
6. `OptimisticLockException` → HTTP `409`; NUNCA silenciarla.
7. EventBus solo para post-procesamiento (auditoría, métricas, notificaciones).
8. PROHIBIDO Kafka: nada de `@Incoming`/`@Outgoing` de Reactive Messaging hacia brokers.

