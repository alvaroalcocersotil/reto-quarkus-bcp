package com.bcp.banking.customer.exception;

/**
 * Problem Details RFC 7807 estándar del sistema (copilot-instructions §2).
 */
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String correlationId) {

    private static final String BASE = "https://bcp-challenge.com/errors/";

    public static ProblemDetail of(String code, String title, int status, String detail, String path) {
        return new ProblemDetail(BASE + safeCode(code), title, status, detail, path,
                org.slf4j.MDC.get("correlationId"));
    }

    private static String safeCode(String code) {
        return code == null || code.isBlank() ? "internal-error" : code;
    }
}
