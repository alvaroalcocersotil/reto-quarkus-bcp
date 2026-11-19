package com.bcp.banking.customer.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests @QuarkusTest del customer-service (spec 01 §6).
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerResourceTest {

    static final UUID ACTIVE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID SUSPENDED_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @Order(1)
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void getSeededCustomerActive() {
        given()
            .when().get("/api/v1/customers/" + ACTIVE_ID)
        .then()
            .statusCode(200)
            .body("documentType", equalTo("DNI"))
            .body("documentNumber", equalTo("87654321"))
            .body("status", equalTo("ACTIVE"));
    }

    @Test
    @Order(2)
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void getSeededCustomerSuspended() {
        given()
            .when().get("/api/v1/customers/" + SUSPENDED_ID)
        .then()
            .statusCode(200)
            .body("status", equalTo("SUSPENDED"));
    }

    @Test
    @Order(3)
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void getUnknownCustomerReturnsProblemDetail() {
        UUID random = UUID.randomUUID();
        given()
            .when().get("/api/v1/customers/" + random)
        .then()
            .statusCode(404)
            .contentType(containsString("application/problem+json"))
            .body("type", containsString("customer-not-found"))
            .body("correlationId", notNullValue());
    }

    @Test
    @Order(4)
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void validateActiveCustomerIsEligible() {
        given()
            .when().get("/api/v1/customers/" + ACTIVE_ID + "/validate")
        .then()
            .statusCode(200)
            .body("eligibleForTransactions", is(true));
    }

    @Test
    @Order(5)
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void validateSuspendedCustomerReturns422() {
        given()
            .when().get("/api/v1/customers/" + SUSPENDED_ID + "/validate")
        .then()
            .statusCode(422)
            .body("type", containsString("customer-not-active"));
    }

    @Test
    @Order(6)
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void createCustomerReturns201WithLocation() {
        String body = """
                {
                  "firstName": "Ana",
                  "lastName": "Torres",
                  "documentType": "DNI",
                  "documentNumber": "99887766",
                  "email": "ana.torres@mail.com",
                  "phoneNumber": "+51911223344"
                }
                """;
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/v1/customers")
        .then()
            .statusCode(201)
            .header("Location", notNullValue())
            .body("status", equalTo("ACTIVE"));
    }

    @Test
    @Order(7)
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void createDuplicateDocumentReturns409() {
        String body = """
                {
                  "firstName": "Otro",
                  "lastName": "García",
                  "documentType": "DNI",
                  "documentNumber": "87654321",
                  "email": "otro.garcia@mail.com"
                }
                """;
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/v1/customers")
        .then()
            .statusCode(409)
            .body("type", containsString("duplicate-customer"));
    }

    @Test
    @Order(8)
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void createUserWithoutAdminRoleIsForbidden() {
        String body = """
                {
                  "firstName": "X",
                  "lastName": "Y",
                  "documentType": "DNI",
                  "documentNumber": "00000000",
                  "email": "x@y.com"
                }
                """;
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/v1/customers")
        .then()
            .statusCode(403);
    }

    @Test
    @Order(8)
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void correlationIdEchoesWhenProvided() {
        given()
            .header("X-Correlation-ID", "test-correlation-123")
            .when().get("/api/v1/customers/" + ACTIVE_ID)
        .then()
            .statusCode(200)
            .header("X-Correlation-ID", equalTo("test-correlation-123"));
    }
}
