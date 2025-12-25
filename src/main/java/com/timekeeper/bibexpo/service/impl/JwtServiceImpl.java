package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.JwtConfig;
import com.timekeeper.bibexpo.exception.JwtAuthenticationException;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtServiceImpl implements JwtService {

    private final JwtConfig jwtConfig;

    @Override
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();

        // Add custom claims
        claims.put("userId", user.getId());
        claims.put("role", user.getRole().name());

        // Add organization ID if present (null for system-level roles)
        if (user.getOrganization() != null) {
            claims.put("organizationId", user.getOrganization().getId());
        }

        // Add email if present
        if (user.getEmail() != null) {
            claims.put("email", user.getEmail());
        }

        // Add full name if present
        if (user.getFullName() != null) {
            claims.put("fullName", user.getFullName());
        }

        // Add authorities list
        claims.put("authorities", user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .issuer(jwtConfig.getIssuer())
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    @Override
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
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
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            throw new JwtAuthenticationException("JWT token has expired", e);
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            throw new JwtAuthenticationException("Invalid JWT signature", e);
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
            throw new JwtAuthenticationException("Malformed JWT token", e);
        } catch (Exception e) {
            log.error("JWT parsing error: {}", e.getMessage());
            throw new JwtAuthenticationException("JWT token parsing failed", e);
        }
    }

    @Override
    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    @Override
    public Long extractOrganizationId(String token) {
        Claims claims = extractAllClaims(token);
        Object orgId = claims.get("organizationId");
        if (orgId == null) {
            return null;
        }
        if (orgId instanceof Integer) {
            return ((Integer) orgId).longValue();
        }
        return (Long) orgId;
    }

    @Override
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userId = claims.get("userId");
        if (userId == null) {
            return null;
        }
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return (Long) userId;
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
