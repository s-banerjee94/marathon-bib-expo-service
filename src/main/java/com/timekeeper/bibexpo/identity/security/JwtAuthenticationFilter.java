package com.timekeeper.bibexpo.identity.security;

import com.timekeeper.bibexpo.shared.error.AuthErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Authenticates REST requests from their Bearer access token. A token from a signed-out device or
 * a locked/disabled account gets a 401 with a specific code the frontend acts on; any other failure
 * leaves the request unauthenticated for the entry point to reject.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AccessTokenAuthenticator authenticator;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        switch (authenticator.authenticate(request, "api")) {
            case SESSION_INACTIVE -> {
                writeUnauthorized(request, response, AuthErrorCode.SESSION_INVALIDATED,
                        "This device has been signed out. Please log in again.");
                return;
            }
            case ACCOUNT_LOCKED -> {
                writeUnauthorized(request, response, AuthErrorCode.ACCOUNT_LOCKED,
                        "Your account has been locked. Please contact a platform administrator.");
                return;
            }
            case ACCOUNT_DISABLED -> {
                writeUnauthorized(request, response, AuthErrorCode.ACCOUNT_DISABLED,
                        "Your account has been disabled. Please contact an administrator.");
                return;
            }
            default -> {
            }
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
