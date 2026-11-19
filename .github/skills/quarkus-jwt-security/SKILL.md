---
name: quarkus-jwt-security
description: Guía para configurar SmallRye JWT en Quarkus 3.x, generar llaves RSA privateKey.pem/publicKey.pem para pruebas locales, crear usuarios de prueba con roles ROLE_USER y ROLE_ADMIN, proteger endpoints con @RolesAllowed y estandarizar errores con ExceptionMapper (RFC 7807). Activar al implementar seguridad, login/test-tokens o mappers de excepciones.
---

# Skill: Quarkus SmallRye JWT + Exception Mappers

## 1. Dependencia (`pom.xml`)

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-jwt</artifactId>
</dependency>
```

## 2. Generar llaves RSA para pruebas locales

```powershell
# Windows PowerShell (Git Bash u OpenSSL disponible) — ejecutar UNA vez por servicio
openssl genrsa -out privateKey.pem 2048
openssl rsa -in privateKey.pem -pubout -out publicKey.pem
```

Colocar en `src/main/resources/` (solo dev/test). **Nunca** commitear llaves productivas.

## 3. Configuración (`application.properties`)

```properties
# dev / test: verificación con llave pública local
mp.jwt.verify.publickey.location=publicKey.pem
mp.jwt.verify.issuer=https://bcp-challenge.com/issuer
quarkus.smallrye-jwt.enabled=true

# CORS
quarkus.http.cors=true
quarkus.http.cors.origins=/
quarkus.http.cors.headers=X-Correlation-ID,Idempotency-Key,Authorization,Content-Type

# %prod (COMENTADO — PostgreSQL + JWKS)
# %prod.quarkus.datasource.db-kind=postgresql
# %prod.quarkus.datasource.username=app
# %prod.quarkus.datasource.password=${DB_PASSWORD}
# %prod.mp.jwt.verify.publickey.location=https://auth.example.com/.well-known/jwks.json
```

## 4. Proteger endpoints

```java
@Path("/api/v1/transfers")
@ApplicationScoped
public class TransferResource {

    @POST
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public Response create(@Valid TransferRequest request,
                           @HeaderParam("Idempotency-Key") String idempotencyKey) {
        return Response.status(Response.Status.CREATED)
                       .entity(transferService.execute(request, idempotencyKey))
                       .build();
    }
}
```

## 5. Generar tokens de prueba (`@QuarkusTest`)

```java
public class TestJwtFactory {
    public static String userToken()   { return token("user-1", "ROLE_USER"); }
    public static String adminToken()  { return token("admin-1", "ROLE_ADMIN", "ROLE_USER"); }

    private static String token(String sub, String... groups) {
        return Jwt.issuer("https://bcp-challenge.com/issuer")
                  .upn(sub).subject(sub)
                  .groups(Set.of(groups))
                  .expiresIn(Duration.ofMinutes(30))
                  .sign(Duration.ofMinutes(30)); // usa privateKey.pem del classpath
    }
}

// Uso en RestAssured:
given()
    .header("Authorization", "Bearer " + TestJwtFactory.userToken())
    .header("Idempotency-Key", UUID.randomUUID().toString())
    .contentType(ContentType.JSON)
    .body(request)
.when().post("/api/v1/transfers")
.then().statusCode(201);
```

Para exponer un endpoint de login/dev (opcional), usar `quarkus-smallrye-jwt-build` y un `POST /api/v1/auth/token` solo en perfiles dev/test.

## 6. ExceptionMapper estandarizado (RFC 7807)

```java
public record ProblemDetail(URI type, String title, int status, String detail,
                            URI instance, String correlationId) {}

@Provider
public class BusinessRuleExceptionMapper implements ExceptionMapper<BusinessRuleException> {
    @Inject CorrelationIdFilter correlation; // para leer el MDC

    @Override
    public Response toResponse(BusinessRuleException ex) {
        int status = "insufficient-funds".equals(ex.code()) ? 422
                   : "same-account-transfer".equals(ex.code()) ? 422 : 400;
        ProblemDetail body = new ProblemDetail(
                URI.create("https://bcp-challenge.com/errors/" + ex.code()),
                ex.title(), status, ex.getMessage(),
                URI.create("/api/v1/transfers"), MDC.get("correlationId"));
        return Response.status(status)
                       .type(MediaType.APPLICATION_PROBLEM_JSON)
                       .entity(body).build();
    }
}
```

Mappers mínimos requeridos por servicio:

| Mapper | Maneja | HTTP |
|---|---|---|
| `BusinessRuleExceptionMapper` | reglas de negocio | 400/422 |
| `NotFoundExceptionMapper` (`WebApplicationException` 404) | recursos inexistentes | 404 |
| `ValidationExceptionMapper` | `ConstraintViolationException` | 400 |
| `OptimisticLockExceptionMapper` | `OptimisticLockException` | 409 |
| `InfrastructureExceptionMapper` | timeouts / CircuitBreaker abierto | 503 |
| `GenericExceptionMapper` | `Exception` (catch-all) | 500 |

## 7. Checklist de seguridad

- [ ] `smallrye-jwt` en los 3 servicios; sin rutas de negocio anónimas.
- [ ] `/internal/**` exige `ROLE_ADMIN`; excluido del OpenAPI público.
- [ ] `401` sin token, `403` con rol insuficiente (testeado con `@QuarkusTest`).
- [ ] JWT nunca logueado completo; PII enmascarada.
- [ ] `%prod` con JWKS comentado y listo para habilitar.
