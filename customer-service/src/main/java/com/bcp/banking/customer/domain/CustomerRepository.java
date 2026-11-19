package com.bcp.banking.customer.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio Panache para Customer (spec 01 §1).
 */
@ApplicationScoped
public class CustomerRepository implements PanacheRepositoryBase<Customer, UUID> {

    public Optional<Customer> findByIdOptional(UUID id) {
        return Optional.ofNullable(findById(id));
    }

    public Optional<Customer> findByDocument(DocumentType type, String number) {
        return find("documentType = ?1 and lower(documentNumber) = ?2",
                type, number.toLowerCase()).firstResultOptional();
    }

    public Optional<Customer> findByEmailIgnoreCase(String email) {
        return find("lower(email)", email.toLowerCase()).firstResultOptional();
    }

    public List<Customer> findActive() {
        return list("status", CustomerStatus.ACTIVE);
    }
}
