package com.timekeeper.bibexpo.participantaccess.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class ShortCodeGenerator {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;

    private final byte[] keyBytes;

    public ShortCodeGenerator(@Value("${app.participant-access.qr-secret}") String secret) {
        this.keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deterministic short code derived from event and bib via keyed HMAC.
     * The same inputs always yield the same code (idempotent regeneration); the
     * key makes codes unguessable from the event/bib values alone. The attempt
     * counter produces a fresh deterministic code to resolve a rare collision.
     */
    public String generate(String eventId, String bibNumber, int attempt) {
        String message = eventId + ":" + bibNumber + (attempt == 0 ? "" : ":" + attempt);
        byte[] mac = hmac(message);

        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (mac[i] & 0xFF);
        }
        value &= Long.MAX_VALUE;

        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt((int) (value % ALPHABET.length())));
            value /= ALPHABET.length();
        }
        return sb.toString();
    }

    private byte[] hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Short code generation failed.", e);
        }
    }
}
