package tools;

import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class KeyGenerator {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        PrivateKey priv = kp.getPrivate();
        PublicKey pub = kp.getPublic();

        File dir = new File("licensing_secret");
        if (!dir.exists()) dir.mkdirs();

        String privB64 = Base64.getEncoder().encodeToString(priv.getEncoded());
        String pubB64 = Base64.getEncoder().encodeToString(pub.getEncoded());

        try (FileOutputStream fos = new FileOutputStream(new File(dir, "hde_private_key.pem"))) {
            fos.write(("-----BEGIN PRIVATE KEY-----\n" + privB64 + "\n-----END PRIVATE KEY-----\n").getBytes("UTF-8"));
        }

        try (FileOutputStream fos = new FileOutputStream(new File(dir, "hde_public_key.pem"))) {
            fos.write(("-----BEGIN PUBLIC KEY-----\n" + pubB64 + "\n-----END PUBLIC KEY-----\n").getBytes("UTF-8"));
        }

        System.out.println("PUBLIC_KEY_BASE64=" + pubB64);
        System.out.println("Private key written to licensing_secret/hde_private_key.pem");
    }
}
