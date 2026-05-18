package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.service.CsrfTokenService;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class CsrfTokenServiceImpl implements CsrfTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] buf = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    @Override
    public boolean matches(String headerValue, String cookieValue) {
        if (headerValue == null || cookieValue == null
                || headerValue.isBlank() || cookieValue.isBlank()) {
            return false;
        }
        byte[] a = headerValue.getBytes(StandardCharsets.UTF_8);
        byte[] b = cookieValue.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
