package tools;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Commercial License Key Generator for HDE Studio.
 * Uses RSA-2048 and SHA-256 digital signatures.
 * KEEP THIS TOOL AND THE PRIVATE KEY SECURE!
 */
public class GenerateLicense {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("=======================================================");
            System.out.println("   HDE STUDIO OFFICIAL COMMERCIAL LICENSE GENERATOR   ");
            System.out.println("=======================================================");
            System.out.println("Usage: java -cp . tools.GenerateLicense <customer_email> <plan> [valid_days]");
            System.out.println("Plans:");
            System.out.println("  GLOBAL_PRO  - All-Access Global Pro Edition (India + USA)");
            System.out.println("  USA_PRO     - USA & International Edition");
            System.out.println("  IND_PRO     - India Standard Edition");
            System.out.println("Valid Days: 0 or omit for Lifetime, or e.g. 365 for 1-Year Subscription");
            System.out.println("Example: java -cp . tools.GenerateLicense buyer@cadstudio.com GLOBAL_PRO 0");
            System.exit(1);
        }

        String email = args[0].trim().toLowerCase();
        String plan = args[1].trim().toUpperCase();
        int validDays = (args.length >= 3) ? Integer.parseInt(args[2].trim()) : 0;

        try {
            String licenseKey = generateKey(email, plan, validDays);
            System.out.println("\n=======================================================");
            System.out.println("            HDE STUDIO COMMERCIAL LICENSE             ");
            System.out.println("=======================================================");
            System.out.println("Registered Email : " + email);
            System.out.println("License Plan     : " + plan);
            System.out.println("Duration         : " + (validDays == 0 ? "LIFETIME (Permanent)" : validDays + " Days"));
            System.out.println("-------------------------------------------------------");
            System.out.println("COPY THIS LICENSE KEY TO CUSTOMER:");
            System.out.println(licenseKey);
            System.out.println("=======================================================\n");
        } catch (Exception e) {
            System.err.println("Error generating license: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static String generateKey(String email, String plan, int validDays) throws Exception {
        File privFile = new File("licensing_secret/hde_private_key.pem");
        if (!privFile.exists()) {
            throw new IllegalStateException("Private key not found at " + privFile.getAbsolutePath());
        }

        String pem = new String(Files.readAllBytes(privFile.toPath()), "UTF-8");
        String b64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(b64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(spec);

        long issued = System.currentTimeMillis();
        long expires = (validDays <= 0) ? 0L : issued + (validDays * 86400000L);

        String payload = "v1|" + email.toLowerCase() + "|" + plan + "|" + issued + "|" + expires;
        byte[] payloadBytes = payload.getBytes("UTF-8");

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(payloadBytes);
        byte[] sigBytes = signer.sign();

        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
        String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);

        return "HDE." + payloadB64 + "." + sigB64;
    }
}
