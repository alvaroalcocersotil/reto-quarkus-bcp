package com.bcp.banking.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request de creación de cuenta (spec 02 §1). Número autogenerado si no viene.
 */
public record CreateAccountRequest(
        @NotNull UUID customerId,
        @DecimalMin(value = "0.00") BigDecimal initialBalance,
        @Pattern(regexp = "^PEN$") String currency) {
}
