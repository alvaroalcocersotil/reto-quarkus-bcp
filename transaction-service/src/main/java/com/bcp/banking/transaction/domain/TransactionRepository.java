package com.bcp.banking.transaction.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio Panache para Transaction (spec 03 §2).
 */
@ApplicationScoped
public class TransactionRepository implements PanacheRepositoryBase<Transaction, UUID> {

    public Optional<Transaction> findByIdOptional(UUID id) {
        return Optional.ofNullable(findById(id));
    }

    public Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey) {
        return find("idempotencyKey", idempotencyKey).firstResultOptional();
    }
}
