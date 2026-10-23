package util;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Passwords are never stored as plain text - we store their SHA-256 hash. */
public class Hash {
    public static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(("evms-salt:" + text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
