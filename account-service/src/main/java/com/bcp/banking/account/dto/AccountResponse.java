package com.bcp.banking.account.dto;

import com.bcp.banking.account.domain.Account;
import com.bcp.banking.account.domain.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de cuenta (spec 02 §2). Record inmutable.
 */
public record AccountResponse(
        UUID id,
        String accountNumber,
        UUID customerId,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        Long version,
        Instant createdAt) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(a.id, a.accountNumber, a.customerId, a.balance,
                a.currency, a.status, a.version, a.createdAt);
    }
}
