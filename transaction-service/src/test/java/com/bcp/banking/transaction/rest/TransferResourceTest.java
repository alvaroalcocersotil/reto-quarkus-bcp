package com.bcp.banking.transaction.rest;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bcp.banking.transaction.client.AccountClient;
import com.bcp.banking.transaction.client.CustomerClient;
import com.bcp.banking.transaction.dto.OperationResponse;
import com.bcp.banking.transaction.dto.ValidationResponse;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.ws.rs.WebApplicationException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests @QuarkusTest del orquestador Saga (spec 03 §6) con REST Clients mockeados.
 */
@QuarkusTest
class TransferResourceTest {

    static final UUID SRC = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID TGT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    static final UUID CUSTOMER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @InjectMock(convertScopes = true)
    @org.eclipse.microprofile.rest.client.inject.RestClient
    CustomerClient customerClient;

    @InjectMock(convertScopes = true)
    @org.eclipse.microprofile.rest.client.inject.RestClient
    AccountClient accountClient;

    static OperationResponse op() {
        return new OperationResponse(SRC, "ACC-000001", new BigDecimal("900.00"), 1L);
    }

    static String transferBody() {
        return """
                {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 100.00, "customerId": "%s"}
                """.formatted(SRC, TGT, CUSTOMER);
    }

    void stubHappyPath() {
        when(accountClient.getBalance(any())).thenReturn(op());
        when(customerClient.validate(any()))
                .thenReturn(new ValidationResponse(CUSTOMER, ValidationResponse.CustomerStatusRef.ACTIVE, true));
        when(accountClient.debit(any(), any())).thenReturn(op());
        when(accountClient.credit(any(), any())).thenReturn(op());
        when(accountClient.compensateDebit(any(), any())).thenReturn(op());
    }

    // ===== CAMILO FELIZ: flujo del Saga y compensaciones =====

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void successfulTransferReturns201AndCompleted() {
        stubHappyPath();
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(transferBody())
        .when().post("/api/v1/transfers")
        .then()
            .statusCode(201)
            .body("status", equalTo("COMPLETED"));
        verify(accountClient).credit(any(), any());
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void idempotentReplayReturns200WithSameResult() {
        stubHappyPath();
        String key = UUID.randomUUID().toString();
        given().contentType(ContentType.JSON).header("Idempotency-Key", key)
            .body(transferBody())
        .when().post("/api/v1/transfers")
        .then().statusCode(201);

        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", key)
            .body(transferBody())
        .when().post("/api/v1/transfers")
        .then().statusCode(200)
            .body("status", equalTo("COMPLETED"));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void idempotencyConflictReturns409() {
        stubHappyPath();
        String key = UUID.randomUUID().toString();
        given().contentType(ContentType.JSON).header("Idempotency-Key", key)
            .body(transferBody())
        .when().post("/api/v1/transfers")
        .then().statusCode(201);

        String conflict = """
                {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 200.00, "customerId": "%s"}
                """.formatted(SRC, TGT, CUSTOMER);
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", key)
            .body(conflict)
        .when().post("/api/v1/transfers")
        .then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void missingIdempotencyKeyReturns400() {
        given()
            .contentType(ContentType.JSON)
            .body(transferBody())
        .when().post("/api/v1/transfers")
        .then().statusCode(400);
    }


    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void validationFailureReturns422WithoutDebit() {
        when(customerClient.validate(any())).thenReturn(
                new ValidationResponse(CUSTOMER, ValidationResponse.CustomerStatusRef.SUSPENDED, false));
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(transferBody())
        .when().post("/api/v1/transfers")
        .then()
            .statusCode(422)
            .body("type", containsString("customer-not-eligible"));
        verify(accountClient, never()).debit(any(), any());
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void insufficientFundsInDebitFailsWithoutCompensation() {
        when(accountClient.getBalance(any())).thenReturn(op());
        when(customerClient.validate(any()))
                .thenReturn(new ValidationResponse(CUSTOMER, ValidationResponse.CustomerStatusRef.ACTIVE, true));
        when(accountClient.debit(any(), any())).thenThrow(new WebApplicationException(422));

        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(transferBody())
        .when().post("/api/v1/transfers")
        .then().statusCode(422)
            .body("type", containsString("debit-failed"));
        verify(accountClient, never()).credit(any(), any());
        verify(accountClient, never()).compensateDebit(any(), any());
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void creditFailureTriggersDebitCompensation() {
        stubHappyPath();
        when(accountClient.credit(any(), any())).thenThrow(new WebApplicationException(503));

        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(transferBody())
        .when().post("/api/v1/transfers")
        .then().statusCode(502)
            .body("type", containsString("credit-failed"));

        verify(accountClient).compensateDebit(any(), any());
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void unknownTransferReturns404() {
        given().when().get("/api/v1/transfers/" + UUID.randomUUID())
            .then().statusCode(404)
            .body("type", containsString("transfer-not-found"));
    }
}
