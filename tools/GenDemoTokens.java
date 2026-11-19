import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Generador de tokens JWT (RS256) para la demo en vivo con Insomnia.
 *
 * Usa la llave privada compartida de dev/test (transaction-service/src/test/resources/privateKey.pem)
 * y genera dos tokens con vigencia de 24 horas:
 *   - ROLE_USER  (consultas y transferencias estándar)
 *   - ROLE_ADMIN (creación de clientes, endpoints /internal/**)
 *
 * Ejecución (desde la raíz del proyecto):
 *   java tools/GenDemoTokens.java
 *   java tools/GenDemoTokens.java <ruta-otra-privateKey.pem>
 *
 * Claims alineados con la config de los 3 servicios:
 *   mp.jwt.verify.issuer=https://bcp-challenge.com/issuer
 *   (el rol se lee del claim "groups" — convención SmallRye JWT)
 */
public class GenDemoTokens {

    static final String ISSUER = "https://bcp-challenge.com/issuer";
    static final long TTL_SECONDS = 24 * 3600; // 24 horas

    public static void main(String[] args) throws Exception {
        Path keyPath = (args.length > 0)
                ? Path.of(args[0])
                : Path.of("transaction-service", "src", "test", "resources", "privateKey.pem");
        if (!Files.exists(keyPath)) {
            System.err.println("No se encontró la llave privada en: " + keyPath.toAbsolutePath());
            System.err.println("Uso: java tools/GenDemoTokens.java [ruta/privateKey.pem]");
            System.exit(1);
        }
        PrivateKey key = readPkcs8(keyPath);
        long now = System.currentTimeMillis() / 1000;

        String userToken = sign(key, "demo-user@bcp.com", "ROLE_USER", now);
        String adminToken = sign(key, "demo-admin@bcp.com", "ROLE_ADMIN", now);

        System.out.println("=".repeat(100));
        System.out.println("Tokens de demo (expiran en 24h) — copiar en el environment de Insomnia");
        System.out.println("=".repeat(100));
        System.out.println("\n# jwt_user  (ROLE_USER — consultas y transferencias)");
        System.out.println(userToken);
        System.out.println("\n# jwt_admin (ROLE_ADMIN — crear clientes, endpoints /internal/**)");
        System.out.println(adminToken);
        System.out.println("\n# Verificación rápida:");
        System.out.println("#   curl -H \"Authorization: Bearer " + userToken.substring(0, 40)
                + "...\" http://localhost:8081/api/v1/customers");
    }

    /** Lee un PEM PKCS#8 (-----BEGIN PRIVATE KEY-----) y lo convierte en PrivateKey RSA. */
    static PrivateKey readPkcs8(Path pemPath) throws Exception {
        String pem = Files.readString(pemPath)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** Construye y firma un JWT RS256 con los claims del perfil indicado. */
    static String sign(PrivateKey key, String subject, String role, long iat) throws Exception {
        long exp = iat + TTL_SECONDS;
        String header = b64url(("{\"alg\":\"RS256\",\"typ\":\"JWT\"}").getBytes());
        String payload = b64url(("{\"iss\":\"" + ISSUER
                + "\",\"sub\":\"" + subject
                + "\",\"upn\":\"" + subject
                + "\",\"groups\":[\"" + role
                + "\"],\"iat\":" + iat
                + ",\"exp\":" + exp
                + ",\"jti\":\"" + UUID.randomUUID()
                + "\"}").getBytes());
        String signingInput = header + "." + payload;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(signingInput.getBytes());
        return signingInput + "." + b64url(sig.sign());
    }

    static String b64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
