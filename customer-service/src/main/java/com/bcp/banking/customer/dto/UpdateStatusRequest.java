package com.bcp.banking.customer.dto;

import com.bcp.banking.customer.domain.CustomerStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Request para cambiar estado del cliente (PATCH /{id}/status).
 */
public record UpdateStatusRequest(@NotNull CustomerStatus status) {
}
