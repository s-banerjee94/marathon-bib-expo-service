package com.timekeeper.bibexpo.security;

import com.timekeeper.bibexpo.exception.AuthErrorCode;
import com.timekeeper.bibexpo.service.JwtService;
import com.timekeeper.bibexpo.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final SessionService sessionService;
    private final UserDetailsChecker accountStatusChecker = new AccountStatusUserDetailsChecker();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            final String username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (!jwtService.isTokenValid(jwt, userDetails, JwtService.TYPE_ACCESS)) {
                    log.debug("Invalid access token for user: {}", username);
                    filterChain.doFilter(request, response);
                    return;
                }

                String tokenSid = jwtService.extractSid(jwt);
                String activeSid = sessionService.getActiveSid(username);
                if (tokenSid == null || !tokenSid.equals(activeSid)) {
                    // A stale token is expected traffic after an eviction — reject it without touching
                    // the session row, which now belongs to the newer login (do not end it here).
                    log.info("Stale session token for user {} (sid mismatch) — rejecting request", username);
                    writeUnauthorized(request, response, AuthErrorCode.SESSION_INVALIDATED,
                            "Session invalidated by another login. Please log in again.");
                    return;
                }

                try {
                    accountStatusChecker.check(userDetails);
                } catch (AccountStatusException e) {
                    log.info("Blocking request for {} — {}", username, e.getMessage());
                    boolean locked = e instanceof LockedException;
                    writeUnauthorized(request, response,
                            locked ? AuthErrorCode.ACCOUNT_LOCKED : AuthErrorCode.ACCOUNT_DISABLED,
                            locked ? "Your account has been locked. Please contact an platform administrator."
                                    : "Your account has been disabled. Please contact an administrator.");
                    return;
                }

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Successfully authenticated user: {}", username);
            }
        } catch (Exception e) {
            log.error("JWT authentication error: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                   String code, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"code\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                Instant.now(), code, message, request.getRequestURI()
        );
        response.getWriter().write(body);
    }
}
