package com.bcp.banking.transaction.security;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.smallrye.jwt.build.Jwt;

/**
 * Genera el token de servicio (ROLE_ADMIN) para invocar /internal/** de account-service
 * y las APIs de customer-service (spec 03 §3, skill quarkus-jwt-security).
 * En dev/test firma con privateKey.pem local; en %prod se usa un IdP real (config comentada).
 */
@ApplicationScoped
public class ServiceTokenProvider {

    public String token() {
        return Jwt.issuer("https://bcp-challenge.com/issuer")
                .upn("transaction-service")
                .groups(Set.of("ROLE_ADMIN"))
                .expiresIn(300)
                .sign();
    }
}

