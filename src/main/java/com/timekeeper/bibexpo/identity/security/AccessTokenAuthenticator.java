package com.timekeeper.bibexpo.identity.security;

import com.timekeeper.bibexpo.identity.exception.JwtAuthenticationException;
import com.timekeeper.bibexpo.identity.service.JwtService;
import com.timekeeper.bibexpo.identity.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

/**
 * The one set of checks behind every Bearer access token, shared by the REST and MCP filters so the
 * two can never drift apart: signature and expiry, token type, an active session (so a signed-out
 * device cannot keep acting) and account status. Each filter only decides what a failed
 * {@link Result} means for its response.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessTokenAuthenticator {

    private static final String BEARER = "Bearer ";

    public enum Result { AUTHENTICATED, NOT_ATTEMPTED, INVALID, SESSION_INACTIVE, ACCOUNT_LOCKED, ACCOUNT_DISABLED }

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final SessionService sessionService;
    private final UserDetailsChecker accountStatusChecker = new AccountStatusUserDetailsChecker();

    /**
     * Checks the request's Bearer access token and, when every check passes, puts its user in the
     * SecurityContext. Never throws: each failure is logged once and reported as a {@link Result}.
     *
     * @param channel which entry point is asking ({@code api} or {@code mcp}), for the logs
     */
    public Result authenticate(HttpServletRequest request, String channel) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            return Result.NOT_ATTEMPTED;
        }

        String jwt = header.substring(BEARER.length());
        String username = null;
        try {
            username = jwtService.extractUsername(jwt);
            UserDetails user = userDetailsService.loadUserByUsername(username);

            if (!jwtService.isTokenValid(jwt, user, JwtService.TYPE_ACCESS)) {
                return rejected(Result.INVALID, Level.WARN, "wrong_token_type", username, channel);
            }

            String sid = jwtService.extractSid(jwt);
            if (sid == null || !sessionService.getActiveSids(username).contains(sid)) {
                // Expected after a device is signed out or evicted, hence INFO.
                return rejected(Result.SESSION_INACTIVE, Level.INFO, "session_inactive", username, channel);
            }

            accountStatusChecker.check(user);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return Result.AUTHENTICATED;
        } catch (LockedException e) {
            return rejected(Result.ACCOUNT_LOCKED, Level.WARN, "account_locked", username, channel);
        } catch (AccountStatusException e) {
            return rejected(Result.ACCOUNT_DISABLED, Level.WARN, "account_disabled", username, channel);
        } catch (JwtAuthenticationException e) {
            return Result.INVALID; // already classified and logged where the token was parsed
        } catch (UsernameNotFoundException e) {
            return rejected(Result.INVALID, Level.WARN, "user_not_found", username, channel);
        } catch (RuntimeException e) {
            log.atError().addKeyValue("channel", channel).setCause(e).log("Token authentication failed unexpectedly");
            return Result.INVALID;
        }
    }

    private Result rejected(Result result, Level level, String reason, String username, String channel) {
        log.atLevel(level)
                .addKeyValue("event", "auth.token.rejected")
                .addKeyValue("reason", reason)
                .addKeyValue("username", username)
                .addKeyValue("channel", channel)
                .log("Token rejected");
        return result;
    }
}
