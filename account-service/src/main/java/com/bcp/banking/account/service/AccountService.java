package com.bcp.banking.account.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bcp.banking.account.domain.Account;
import com.bcp.banking.account.domain.AccountRepository;
import com.bcp.banking.account.domain.AccountStatus;
import com.bcp.banking.account.dto.AccountResponse;
import com.bcp.banking.account.dto.CreateAccountRequest;
import com.bcp.banking.account.dto.OperationRequest;
import com.bcp.banking.account.dto.OperationResponse;
import com.bcp.banking.account.exception.BusinessRuleException;
import com.bcp.banking.account.exception.NotFoundException;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Lógica de negocio de cuentas (spec 02). Transacciones SOLO aquí.
 * @Version en Account protege contra débitos concurrentes (optimistic locking).
 */
@ApplicationScoped
public class AccountService {

    private static final org.jboss.logging.Logger LOG =
            org.jboss.logging.Logger.getLogger(AccountService.class);

    @Inject
    AccountRepository repository;

    public AccountResponse getById(UUID id) {
        return repository.findByIdOptional(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> new NotFoundException("Account " + id + " not found"));
    }

    public AccountResponse getByAccountNumber(String accountNumber) {
        return repository.findByAccountNumber(accountNumber)
                .map(AccountResponse::from)
                .orElseThrow(() -> new NotFoundException("Account " + accountNumber + " not found"));
    }

    public List<AccountResponse> list(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        PanacheQuery<Account> query = repository.findAll().page(safePage, safeSize);
        return query.list().stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse create(UUID customerId, BigDecimal initialBalance, String currency) {
        Account a = new Account();
        a.accountNumber = nextAccountNumber();
        a.customerId = customerId;
        a.balance = initialBalance == null ? BigDecimal.ZERO : initialBalance;
        a.currency = currency == null ? "PEN" : currency;
        repository.persist(a);
        return AccountResponse.from(a);
    }

    private String nextAccountNumber() {
        long count = repository.count() + 1;
        String candidate;
        do {
            candidate = "ACC-%06d".formatted(count);
            count++;
        } while (repository.findByAccountNumber(candidate).isPresent());
        return candidate;
    }

    // ============ Operaciones internas del Saga (spec 02 §3) ============

    @Transactional
    public OperationResponse debit(UUID accountId, OperationRequest req) {
        Account a = repository.findByIdOptional(accountId)
                .orElseThrow(() -> new NotFoundException("Account " + accountId + " not found"));
        if (a.status == AccountStatus.BLOCKED) {
            throw new BusinessRuleException("account-blocked",
                    "Account %s is BLOCKED".formatted(a.accountNumber));
        }
        if (a.status == AccountStatus.CLOSED) {
            throw new BusinessRuleException("account-closed",
                    "Account %s is CLOSED".formatted(a.accountNumber));
        }
        if (!a.hasSufficientBalance(req.amount())) {
            throw new BusinessRuleException("insufficient-funds",
                    "Account %s has balance %s, cannot debit %s"
                            .formatted(a.accountNumber, a.balance, req.amount()));
        }
        a.balance = a.balance.subtract(req.amount());
        LOG.infof("DEBIT tx=%s account=%s amount=%s newBalance=%s v%d",
                req.transactionId(), a.accountNumber, req.amount(), a.balance, a.version);
        return toResponse(a);
    }

    @Transactional
    public OperationResponse credit(UUID accountId, OperationRequest req) {
        Account a = repository.findByIdOptional(accountId)
                .orElseThrow(() -> new NotFoundException("Account " + accountId + " not found"));
        if (a.status == AccountStatus.CLOSED) {
            throw new BusinessRuleException("account-closed",
                    "Account %s is CLOSED".formatted(a.accountNumber));
        }
        a.balance = a.balance.add(req.amount());
        LOG.infof("CREDIT tx=%s account=%s amount=%s newBalance=%s v%d",
                req.transactionId(), a.accountNumber, req.amount(), a.balance, a.version);
        return toResponse(a);
    }

    /** Compensación del débito ya aplicado: reintegra el monto (spec 02 §3). */
    @Transactional
    public OperationResponse compensateDebit(UUID accountId, OperationRequest req) {
        Account a = repository.findByIdOptional(accountId)
                .orElseThrow(() -> new NotFoundException("Account " + accountId + " not found"));
        a.balance = a.balance.add(req.amount());
        LOG.infof("COMPENSATE-DEBIT tx=%s account=%s amount=%s newBalance=%s",
                req.transactionId(), a.accountNumber, req.amount(), a.balance);
        return toResponse(a);
    }

    /** Compensación del crédito ya aplicado: retira el monto acreditado. */
    @Transactional
    public OperationResponse compensateCredit(UUID accountId, OperationRequest req) {
        Account a = repository.findByIdOptional(accountId)
                .orElseThrow(() -> new NotFoundException("Account " + accountId + " not found"));
        a.balance = a.balance.subtract(req.amount());
        LOG.infof("COMPENSATE-CREDIT tx=%s account=%s amount=%s newBalance=%s",
                req.transactionId(), a.accountNumber, req.amount(), a.balance);
        return toResponse(a);
    }

    private OperationResponse toResponse(Account a) {
        return new OperationResponse(a.id, a.accountNumber, a.balance, a.version);
    }
}
