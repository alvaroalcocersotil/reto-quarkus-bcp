package com.bcp.banking.customer.dto;

import com.bcp.banking.customer.domain.Customer;
import com.bcp.banking.customer.domain.CustomerStatus;
import com.bcp.banking.customer.domain.DocumentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de cliente (spec 01 §2). DTO inmutable como record (regla copilot-instructions §1).
 */
public record CustomerResponse(
        UUID id,
        String firstName,
        String lastName,
        DocumentType documentType,
        String documentNumber,
        String email,
        String phoneNumber,
        CustomerStatus status,
        Instant createdAt) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.id, c.firstName, c.lastName, c.documentType,
                c.documentNumber, c.email, c.phoneNumber, c.status, c.createdAt);
    }
}
