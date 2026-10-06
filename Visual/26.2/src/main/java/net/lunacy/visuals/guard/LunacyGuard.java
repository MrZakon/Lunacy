package net.lunacy.visuals.guard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class LunacyGuard {
    private static final byte[] SEED = {
            0x4C, 0x55, 0x4E, 0x41, 0x43, 0x59, 0x5F, 0x53, 0x45, 0x43, 0x5F,
            0x41, 0x55, 0x54, 0x48, 0x5F, 0x78, 0x38, 0x46, 0x32, 0x44, 0x5F,
            0x4B, 0x45, 0x52, 0x4E, 0x45, 0x4C, 0x5F, 0x47, 0x55, 0x41, 0x52, 0x44
    };

    private LunacyGuard() {}

    public static void verifyOrHalt() {
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}