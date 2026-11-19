package com.bcp.banking.customer.dto;

import com.bcp.banking.customer.domain.DocumentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request de creación de cliente (spec 01 §1).
 */
public record CreateCustomerRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 20) String documentNumber,
        @NotBlank @Email String email,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$") String phoneNumber) {
}
