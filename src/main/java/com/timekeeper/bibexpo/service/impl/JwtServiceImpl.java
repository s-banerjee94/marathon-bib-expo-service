package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.JwtConfig;
import com.timekeeper.bibexpo.exception.JwtAuthenticationException;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtServiceImpl implements JwtService {

    private final JwtConfig jwtConfig;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    void loadKeys() {
        try {
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            this.privateKey = rsa.generatePrivate(new PKCS8EncodedKeySpec(readDer(jwtConfig.getPrivateKeyLocation(), "private")));
            this.publicKey = rsa.generatePublic(new X509EncodedKeySpec(readDer(jwtConfig.getPublicKeyLocation(), "public")));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid RSA key material for JWT signing.", e);
        }
    }

    @Override
    public String generateAccessToken(User user, String sid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", user.getRole().name());
        claims.put("sid", sid);
        claims.put("type", TYPE_ACCESS);

        if (user.getOrganization() != null) {
            claims.put("organizationId", user.getOrganization().getId());
        }
        if (user.getEmail() != null) {
            claims.put("email", user.getEmail());
        }
        if (user.getFullName() != null) {
            claims.put("fullName", user.getFullName());
        }
        claims.put("authorities", user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        return buildToken(claims, user.getUsername(), jwtConfig.getAccessTokenExpiration());
    }

    @Override
    public String generateRefreshToken(User user, String sid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sid", sid);
        claims.put("type", TYPE_REFRESH);
        return buildToken(claims, user.getUsername(), jwtConfig.getRefreshTokenExpiration());
    }

    private String buildToken(Map<String, Object> claims, String subject, long expirationMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .issuer(jwtConfig.getIssuer())
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    @Override
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    @Override
    public boolean isTokenValid(String token, UserDetails userDetails, String expectedType) {
        try {
            Claims claims = extractAllClaims(token);
            final String username = claims.getSubject();
            final String type = claims.get("type", String.class);
            return username.equals(userDetails.getUsername())
                    && expectedType.equals(type)
                    && claims.getExpiration().after(new Date());
        } catch (ExpiredJwtException e) {
            log.debug("Token expired for user: {}", e.getClaims().getSubject());
            return false;
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            throw new JwtAuthenticationException("Your session has expired. Please log in again.", e);
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            throw new JwtAuthenticationException("Invalid session. Please log in again.", e);
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
            throw new JwtAuthenticationException("Invalid session. Please log in again.", e);
        } catch (Exception e) {
            log.error("JWT parsing error: {}", e.getMessage());
            throw new JwtAuthenticationException("Invalid session. Please log in again.", e);
        }
    }

    @Override
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    @Override
    public Long extractOrganizationId(String token) {
        return toLong(extractAllClaims(token).get("organizationId"));
    }

    @Override
    public Long extractUserId(String token) {
        return toLong(extractAllClaims(token).get("userId"));
    }

    @Override
    public String extractSid(String token) {
        return extractAllClaims(token).get("sid", String.class);
    }

    @Override
    public String extractTokenType(String token) {
        return extractAllClaims(token).get("type", String.class);
    }

    @Override
    public long getAccessTokenExpirationMs() {
        return jwtConfig.getAccessTokenExpiration();
    }

    @Override
    public long getRefreshTokenExpirationMs() {
        return jwtConfig.getRefreshTokenExpiration();
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i.longValue();
        return (Long) value;
    }

    private byte[] readDer(Resource location, String label) {
        try {
            String pem = location.getContentAsString(StandardCharsets.UTF_8);
            String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                    .replaceAll("-----END [^-]+-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(base64);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read JWT " + label + " key from " + location, e);
        }
    }
}
