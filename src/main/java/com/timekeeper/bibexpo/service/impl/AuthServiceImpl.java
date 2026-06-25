package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.JwtConfig;
import com.timekeeper.bibexpo.exception.AccountDisabledException;
import com.timekeeper.bibexpo.exception.CsrfValidationException;
import com.timekeeper.bibexpo.exception.InvalidCredentialsException;
import com.timekeeper.bibexpo.exception.JwtAuthenticationException;
import com.timekeeper.bibexpo.model.dto.audit.AuditEvent;
import com.timekeeper.bibexpo.model.dto.request.LoginRequest;
import com.timekeeper.bibexpo.model.dto.response.LoginResponse;
import com.timekeeper.bibexpo.model.dto.response.RefreshResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.AuthService;
import com.timekeeper.bibexpo.service.CsrfTokenService;
import com.timekeeper.bibexpo.service.JwtService;
import com.timekeeper.bibexpo.service.SessionService;
import com.timekeeper.bibexpo.service.audit.AuditPublisher;
import com.timekeeper.bibexpo.service.cache.AuthUserCache;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JwtConfig jwtConfig;
    private final SessionService sessionService;
    private final CsrfTokenService csrfTokenService;
    private final AuditPublisher auditPublisher;
    private final AuthUserCache authUserCache;

    @Override
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        log.info("Login attempt for user: {}", request.getUsername());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

            validateUserAccount(user, request.getUsername());

            String deviceInfo = buildDeviceInfo(httpRequest);
            String sid = sessionService.startSession(user, deviceInfo);

            String accessToken = jwtService.generateAccessToken(user, sid);
            String refreshToken = jwtService.generateRefreshToken(user, sid);
            String csrfToken = csrfTokenService.generate();

            writeRefreshCookie(httpResponse, refreshToken);
            writeCsrfCookie(httpResponse, csrfToken);

            log.info("Login successful for user: {}", request.getUsername());

            publishLoginAudit(user);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .expiresIn(jwtService.getAccessTokenExpirationMs())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .organizationId(user.getOrganization() != null ? user.getOrganization().getId() : null)
                    .build();

        } catch (BadCredentialsException e) {
            log.error("Invalid credentials for user: {}", request.getUsername());
            throw new InvalidCredentialsException("Invalid username or password");
        } catch (DisabledException e) {
            log.warn("Account disabled: {}", request.getUsername());
            throw new AccountDisabledException("Account is disabled");
        } catch (LockedException e) {
            log.warn("Account locked: {}", request.getUsername());
            throw new AccountDisabledException("Account is locked");
        }
    }

    @Override
    public RefreshResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String csrfHeader = httpRequest.getHeader("X-CSRF-Token");
        String csrfCookie = readCookie(httpRequest, jwtConfig.getCsrfCookieName());
        if (!csrfTokenService.matches(csrfHeader, csrfCookie)) {
            throw new CsrfValidationException("Invalid request. Please refresh and try again.");
        }

        String refreshToken = readCookie(httpRequest, jwtConfig.getRefreshCookieName());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new JwtAuthenticationException("Your session has expired. Please log in again.");
        }

        String username = jwtService.extractUsername(refreshToken);
        String tokenType = jwtService.extractTokenType(refreshToken);
        String oldSid = jwtService.extractSid(refreshToken);

        if (!JwtService.TYPE_REFRESH.equals(tokenType)) {
            throw new JwtAuthenticationException("Invalid session. Please log in again.");
        }

        User user = authUserCache.findByUsername(username);
        if (user == null) {
            throw new JwtAuthenticationException("Your session has expired. Please log in again.");
        }

        if (!user.isEnabled()) {
            sessionService.endSession(user);
            clearAuthCookies(httpResponse);
            throw new AccountDisabledException("Account is disabled");
        }

        String activeSid = sessionService.getActiveSid(username);
        if (activeSid == null || !activeSid.equals(oldSid)) {
            log.warn("Invalid refresh token for user {} — request dropped", username);
            throw new JwtAuthenticationException("Your session has been signed out. Please log in again.");
        }

        sessionService.extendSession(user);

        String newAccessToken = jwtService.generateAccessToken(user, oldSid);
        String newRefreshToken = jwtService.generateRefreshToken(user, oldSid);

        writeRefreshCookie(httpResponse, newRefreshToken);

        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn(jwtService.getAccessTokenExpirationMs())
                .build();
    }

    @Override
    public void logout(User user, HttpServletResponse httpResponse) {
        sessionService.endSession(user);
        clearAuthCookies(httpResponse);
        log.info("User {} logged out", user.getUsername());
    }

    private void validateUserAccount(User user, String username) {
        if (!user.isEnabled()) {
            log.warn("Login attempt for disabled account: {}", username);
            throw new AccountDisabledException("Account is disabled");
        }
        if (!user.isAccountNonLocked()) {
            log.warn("Login attempt for locked account: {}", username);
            throw new AccountDisabledException("Account is locked");
        }
        if (!user.isAccountNonExpired()) {
            log.warn("Login attempt for expired account: {}", username);
            throw new AccountDisabledException("Account has expired");
        }
        if (!user.isCredentialsNonExpired()) {
            log.warn("Login attempt for account with expired credentials: {}", username);
            throw new AccountDisabledException("Credentials have expired");
        }
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private void writeRefreshCookie(HttpServletResponse response, String value) {
        long maxAgeSec = jwtService.getRefreshTokenExpirationMs() / 1000;
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(jwtConfig.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(Boolean.TRUE.equals(jwtConfig.getCookieSecure()))
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAgeSec);
        if (jwtConfig.getCookieDomain() != null && !jwtConfig.getCookieDomain().isBlank()) {
            b.domain(jwtConfig.getCookieDomain());
        }
        response.addHeader("Set-Cookie", b.build().toString());
    }

    private void writeCsrfCookie(HttpServletResponse response, String value) {
        long maxAgeSec = jwtService.getRefreshTokenExpirationMs() / 1000;
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(jwtConfig.getCsrfCookieName(), value)
                .httpOnly(false) // frontend JS must read it
                .secure(Boolean.TRUE.equals(jwtConfig.getCookieSecure()))
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAgeSec);
        if (jwtConfig.getCookieDomain() != null && !jwtConfig.getCookieDomain().isBlank()) {
            b.domain(jwtConfig.getCookieDomain());
        }
        response.addHeader("Set-Cookie", b.build().toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        clearCookie(response, jwtConfig.getRefreshCookieName(), true);
        clearCookie(response, jwtConfig.getCsrfCookieName(), false);
    }

    private void clearCookie(HttpServletResponse response, String name, boolean httpOnly) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, "")
                .httpOnly(httpOnly)
                .secure(Boolean.TRUE.equals(jwtConfig.getCookieSecure()))
                .path("/")
                .sameSite("Lax")
                .maxAge(0);
        if (jwtConfig.getCookieDomain() != null && !jwtConfig.getCookieDomain().isBlank()) {
            b.domain(jwtConfig.getCookieDomain());
        }
        response.addHeader("Set-Cookie", b.build().toString());
    }

    private void publishLoginAudit(User user) {
        String label = (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName() : user.getUsername();
        auditPublisher.publish(AuditEvent.builder()
                .organizationId(user.getOrganization() != null ? user.getOrganization().getId() : 0L)
                .actorUserId(user.getId())
                .actorName(user.getUsername())
                .action(AuditAction.LOGIN)
                .entityType(AuditEntityType.USER)
                .entityId(user.getId().toString())
                .entityLabel(label)
                .description(label + " logged in")
                .occurredAt(Instant.now())
                .build());
    }

    private String buildDeviceInfo(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        String ip = request.getRemoteAddr();
        String combined = (ua == null ? "" : ua) + " | " + (ip == null ? "" : ip);
        return combined.length() > 500 ? combined.substring(0, 500) : combined;
    }
}
