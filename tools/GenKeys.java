import java.io.FileWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Genera par RSA 2048 en formato PEM para dev/test (sin depender de openssl). */
public class GenKeys {
    public static void main(String[] args) throws Exception {
        String pubPath = args[0];
        String privPath = args[1];
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();

        String pubPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        String privPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        try (FileWriter w = new FileWriter(pubPath)) {
            w.write(pubPem);
        }
        try (FileWriter w = new FileWriter(privPath)) {
            w.write(privPem);
        }
        System.out.println("Keys written: " + pubPath + " , " + privPath);
    }
}
