package com.timekeeper.bibexpo.participantaccess.util;

import com.timekeeper.bibexpo.exception.InvalidQrCodeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts/decrypts QR payload (eventId:bibNumber) using AES-GCM.
 * Only this application — with the configured secret — can decrypt the token,
 * making QR codes opaque to generic scanners.
 */
@Component
public class QrTokenCodec {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public QrTokenCodec(@Value("${app.participant-access.qr-secret}") String secret) throws Exception {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encode(String eventId, String bibNumber) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = (eventId + ":" + bibNumber).getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("QR token encoding failed.", e);
        }
    }

    /** Returns [eventId, bibNumber] or throws InvalidQrCodeException on tamper/invalid token. */
    public String[] decode(String token) {
        try {
            byte[] combined = Base64.getUrlDecoder().decode(token);
            if (combined.length <= GCM_IV_LENGTH) {
                throw new InvalidQrCodeException();
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            String plaintext = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            String[] parts = plaintext.split(":", 2);
            if (parts.length != 2) {
                throw new InvalidQrCodeException();
            }
            return parts;
        } catch (InvalidQrCodeException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidQrCodeException();
        }
    }
}
