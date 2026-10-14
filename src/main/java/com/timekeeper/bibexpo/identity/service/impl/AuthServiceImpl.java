package com.timekeeper.bibexpo.identity.service.impl;

import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.audit.api.AuditEvent;
import com.timekeeper.bibexpo.audit.api.AuditPublisher;
import com.timekeeper.bibexpo.identity.config.JwtConfig;
import com.timekeeper.bibexpo.identity.exception.AccountDisabledException;
import com.timekeeper.bibexpo.identity.exception.CsrfValidationException;
import com.timekeeper.bibexpo.identity.exception.InvalidCredentialsException;
import com.timekeeper.bibexpo.identity.exception.JwtAuthenticationException;
import com.timekeeper.bibexpo.identity.model.DeviceDetails;
import com.timekeeper.bibexpo.identity.model.dto.request.LoginRequest;
import com.timekeeper.bibexpo.identity.model.dto.response.LoginResponse;
import com.timekeeper.bibexpo.identity.model.dto.response.RefreshResponse;
import com.timekeeper.bibexpo.identity.service.AuthService;
import com.timekeeper.bibexpo.identity.service.CsrfTokenService;
import com.timekeeper.bibexpo.identity.service.JwtService;
import com.timekeeper.bibexpo.identity.service.SessionService;
import com.timekeeper.bibexpo.identity.service.util.DeviceDetailsResolver;
import com.timekeeper.bibexpo.user.api.AuthUserDirectory;
import com.timekeeper.bibexpo.user.api.UserDirectory;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String ACCOUNT_DISABLED = "Your account has been disabled. Please contact an administrator.";
    private static final String ACCOUNT_LOCKED = "Your account has been locked. Please contact a platform administrator.";
    private static final String INVALID_CREDENTIALS = "Your username, email or password is incorrect.";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final SessionService sessionService;
    private final CsrfTokenService csrfTokenService;
    private final AuditPublisher auditPublisher;
    private final AuthUserDirectory authUserDirectory;
    private final UserDirectory userDirectory;
    private final UserDetailsChecker accountStatusChecker = new AccountStatusUserDetailsChecker();

    @Override
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String identifier = request.getUsername().trim();
        // A username can never hold '@', so anything that does is an email address.
        boolean byEmail = identifier.contains("@");
        String username = byEmail ? userDirectory.findUsernameByEmail(identifier).orElse(null) : identifier;
        String loginBy = byEmail ? "email" : "username";
        try {
            // An unknown email still goes through authentication so it fails in the same time as a wrong password.
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username != null ? username : identifier, request.getPassword())
            );
            accountStatusChecker.check((UserDetails) Objects.requireNonNull(authentication.getPrincipal()));

            // Loaded and cached by the authentication above, so this is a hit, not a query.
            User user = authUserDirectory.findByUsername(username);
            if (user == null) {
                throw loginFailed(username, loginBy, "user_not_found", new InvalidCredentialsException(INVALID_CREDENTIALS));
            }

            DeviceDetails device = DeviceDetailsResolver.resolve(httpRequest);
            String sid = sessionService.startSession(user.getUsername(), user.getRole(), device);

            String accessToken = jwtService.generateAccessToken(user, sid);
            String refreshToken = jwtService.generateRefreshToken(user, sid);
            String csrfToken = csrfTokenService.generate();

            writeRefreshCookie(httpResponse, refreshToken);
            writeCsrfCookie(httpResponse, csrfToken);

            log.atInfo()
                    .addKeyValue("event", "auth.login.succeeded")
                    .addKeyValue("username", user.getUsername())
                    .addKeyValue("sessionId", sid)
                    .log("Login succeeded");

            publishLoginAudit(user);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .expiresInMs(jwtService.getAccessTokenExpirationMs())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .organizationId(user.getOrganization() != null ? user.getOrganization().getId() : null)
                    .build();

        } catch (BadCredentialsException e) {
            throw loginFailed(username, loginBy, "bad_credentials", new InvalidCredentialsException(INVALID_CREDENTIALS));
        } catch (DisabledException e) {
            throw loginFailed(username, loginBy, "account_disabled", new AccountDisabledException(ACCOUNT_DISABLED));
        } catch (LockedException e) {
            throw loginFailed(username, loginBy, "account_locked", new AccountDisabledException(ACCOUNT_LOCKED));
        }
    }

    // username is null for an email that matches no account, so the typed address never reaches the log.
    private RuntimeException loginFailed(String username, String loginBy, String reason, RuntimeException toThrow) {
        log.atWarn()
                .addKeyValue("event", "auth.login.failed")
                .addKeyValue("reason", reason)
                .addKeyValue("loginBy", loginBy)
                .addKeyValue("username", username)
                .log("Login failed");
        return toThrow;
    }

    @Override
    public RefreshResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        requireCsrf(httpRequest);

        String refreshToken = readCookie(httpRequest, jwtConfig.getRefreshCookieName());
        if (refreshToken == null || refreshToken.isBlank()) {
            log.atDebug().addKeyValue("event", "auth.refresh.rejected").addKeyValue("reason", "missing_token")
                    .log("Refresh rejected");
            throw new JwtAuthenticationException("Your session has expired. Please log in again.");
        }

        String username = jwtService.extractUsername(refreshToken);
        String tokenType = jwtService.extractTokenType(refreshToken);
        String oldSid = jwtService.extractSid(refreshToken);

        if (!JwtService.TYPE_REFRESH.equals(tokenType)) {
            throw refreshRejected(username, "wrong_token_type",
                    new JwtAuthenticationException("Invalid session. Please log in again."));
        }

        User user = authUserDirectory.findByUsername(username);
        if (user == null) {
            throw refreshRejected(username, "user_not_found",
                    new JwtAuthenticationException("Your session has expired. Please log in again."));
        }

        try {
            accountStatusChecker.check(user);
        } catch (AccountStatusException e) {
            sessionService.endAllSessions(user.getUsername());
            clearAuthCookies(httpResponse);
            boolean locked = e instanceof LockedException;
            throw refreshRejected(username, locked ? "account_locked" : "account_disabled",
                    new AccountDisabledException(locked ? ACCOUNT_LOCKED : ACCOUNT_DISABLED));
        }

        if (oldSid == null || !sessionService.getActiveSids(username).contains(oldSid)) {
            log.atInfo()
                    .addKeyValue("event", "auth.refresh.rejected")
                    .addKeyValue("reason", "session_inactive")
                    .addKeyValue("username", username)
                    .log("Refresh rejected");
            throw new JwtAuthenticationException("Your session has been signed out. Please log in again.");
        }

        sessionService.markSessionUsed(user.getUsername(), oldSid);

        // The refresh token is kept as issued: the session ends 7 days after login, not after last use.
        String newAccessToken = jwtService.generateAccessToken(user, oldSid);

        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .expiresInMs(jwtService.getAccessTokenExpirationMs())
                .build();
    }

    private RuntimeException refreshRejected(String username, String reason, RuntimeException toThrow) {
        log.atWarn()
                .addKeyValue("event", "auth.refresh.rejected")
                .addKeyValue("reason", reason)
                .addKeyValue("username", username)
                .log("Refresh rejected");
        return toThrow;
    }

    @Override
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        requireCsrf(httpRequest);

        String refreshToken = readCookie(httpRequest, jwtConfig.getRefreshCookieName());
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                String username = jwtService.extractUsername(refreshToken);
                String sid = jwtService.extractSid(refreshToken);
                if (username != null && sid != null) {
                    sessionService.endSession(username, sid);
                    log.atInfo()
                            .addKeyValue("event", "auth.logout")
                            .addKeyValue("username", username)
                            .addKeyValue("sessionId", sid)
                            .log("Logged out");
                }
            } catch (JwtAuthenticationException e) {
                log.debug("Logout could not end a session: the refresh token is no longer valid");
            }
        }
        // Always clear the cookies so the client is signed out even if the session was already gone.
        clearAuthCookies(httpResponse);
    }

    private void requireCsrf(HttpServletRequest request) {
        String csrfHeader = request.getHeader(CSRF_HEADER);
        String csrfCookie = readCookie(request, jwtConfig.getCsrfCookieName());
        if (!csrfTokenService.matches(csrfHeader, csrfCookie)) {
            throw new CsrfValidationException("Invalid request. Please refresh and try again.");
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
        setCookie(response, jwtConfig.getRefreshCookieName(), value, true, refreshCookieMaxAgeSec());
    }

    private void writeCsrfCookie(HttpServletResponse response, String value) {
        // Not httpOnly: the frontend has to read this one back to echo it as the CSRF header.
        setCookie(response, jwtConfig.getCsrfCookieName(), value, false, refreshCookieMaxAgeSec());
    }

    private long refreshCookieMaxAgeSec() {
        return jwtService.getRefreshTokenExpirationMs() / 1000;
    }

    private void clearAuthCookies(HttpServletResponse response) {
        setCookie(response, jwtConfig.getRefreshCookieName(), "", true, 0);
        setCookie(response, jwtConfig.getCsrfCookieName(), "", false, 0);
    }

    private void setCookie(HttpServletResponse response, String name, String value,
                           boolean httpOnly, long maxAgeSec) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(Boolean.TRUE.equals(jwtConfig.getCookieSecure()))
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAgeSec);
        if (jwtConfig.getCookieDomain() != null && !jwtConfig.getCookieDomain().isBlank()) {
            cookie.domain(jwtConfig.getCookieDomain());
        }
        response.addHeader("Set-Cookie", cookie.build().toString());
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
}
