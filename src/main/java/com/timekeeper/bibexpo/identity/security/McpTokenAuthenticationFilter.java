package com.timekeeper.bibexpo.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates the Python agent on the MCP routes ({@code /sse}, {@code /mcp/message}).
 *
 * <p>Accepts only the user's own {@code type=access} token, which the Python agent forwards
 * unchanged (browser → agent → MCP), and applies the same checks as the REST API through
 * {@link AccessTokenAuthenticator}, so server-side RBAC is unchanged. Any failure leaves the
 * context unauthenticated, so the chain's entry point returns 401.
 */
@RequiredArgsConstructor
public class McpTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AccessTokenAuthenticator authenticator;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        authenticator.authenticate(request, "mcp");
        filterChain.doFilter(request, response);
    }
}
