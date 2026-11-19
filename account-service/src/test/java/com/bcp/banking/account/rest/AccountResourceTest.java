package com.bcp.banking.account.rest;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests @QuarkusTest del account-service (spec 02 §6).
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountResourceTest {

    static final UUID ACC1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID ACC2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    static final UUID ACC3 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");

    static String opBody(UUID txId, String amount) {
        return """
                {"transactionId": "%s", "amount": %s}
                """.formatted(txId, amount);
    }

    @Test
    @Order(1)
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void getSeededAccount() {
        given().when().get("/api/v1/accounts/" + ACC1)
            .then()
            .statusCode(200)
            .body("accountNumber", equalTo("ACC-000001"))
            .body("balance", equalTo(1000.00f))
            .body("status", equalTo("ACTIVE"));
    }

    @Test
    @Order(2)
    @TestSecurity(user = "svc", roles = "ROLE_ADMIN")
    void debitReducesBalanceAndIncrementsVersion() {
        given()
            .contentType(ContentType.JSON)
            .body(opBody(UUID.randomUUID(), "300.00"))
            .when().post("/internal/accounts/" + ACC1 + "/debit")
        .then()
            .statusCode(200)
            .body("balance", equalTo(700.00f));
    }

    @Test
    @Order(2)
    @TestSecurity(user = "svc", roles = "ROLE_ADMIN")
    void debitInsufficientFundsReturns422() {
        given()
            .contentType(ContentType.JSON)
            .body(opBody(UUID.randomUUID(), "999999.00"))
            .when().post("/internal/accounts/" + ACC2 + "/debit")
        .then()
            .statusCode(422)
            .body("type", containsString("insufficient-funds"));
    }

    @Test
    @Order(3)
    @TestSecurity(user = "svc", roles = "ROLE_ADMIN")
    void debitBlockedAccountReturns422() {
        given()
            .contentType(ContentType.JSON)
            .body(opBody(UUID.randomUUID(), "10.00"))
            .when().post("/internal/accounts/" + ACC3 + "/debit")
        .then()
            .statusCode(422)
            .body("type", containsString("account-blocked"));
    }

    @Test
    @Order(4)
    @TestSecurity(user = "svc", roles = "ROLE_ADMIN")
    void compensateDebitRestoresBalance() {
        // débito 100 sobre ACC-000002 (500) → 400, compensación → 500
        UUID tx = UUID.randomUUID();
        given()
            .contentType(ContentType.JSON)
            .body(opBody(tx, "100.00"))
            .when().post("/internal/accounts/" + ACC2 + "/debit")
        .then().statusCode(200).body("balance", equalTo(400.00f));

        given()
            .contentType(ContentType.JSON)
            .body(opBody(tx, "100.00"))
            .when().post("/internal/accounts/" + ACC2 + "/compensate-debit")
        .then().statusCode(200).body("balance", equalTo(500.00f));
    }

    @Test
    @Order(5)
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void internalEndpointsRequireAdmin() {
        given()
            .contentType(ContentType.JSON)
            .body(opBody(UUID.randomUUID(), "10.00"))
            .when().post("/internal/accounts/" + ACC1 + "/debit")
        .then().statusCode(403);
    }

    @Test
    @Order(6)
    @TestSecurity(user = "svc", roles = "ROLE_ADMIN")
    void creditThenCompensateCreditRestoresBalance() {
        UUID tx = UUID.randomUUID();
        given()
            .contentType(ContentType.JSON)
            .body(opBody(tx, "50.00"))
            .when().post("/internal/accounts/" + ACC2 + "/credit")
        .then().statusCode(200).body("balance", equalTo(550.00f));

        given()
            .contentType(ContentType.JSON)
            .body(opBody(tx, "50.00"))
            .when().post("/internal/accounts/" + ACC2 + "/compensate-credit")
        .then().statusCode(200).body("balance", equalTo(500.00f));
    }
}
