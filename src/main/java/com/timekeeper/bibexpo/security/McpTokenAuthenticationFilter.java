package com.timekeeper.bibexpo.security;

import com.timekeeper.bibexpo.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates the Python agent on the MCP routes ({@code /sse}, {@code /mcp/message}).
 *
 * <p>Accepts only {@code type=mcp} tokens minted by {@link JwtService#generateAgentToken}. Unlike
 * {@link JwtAuthenticationFilter} it does not enforce the single-session {@code sid} check — an
 * agent turn is a short-lived service credential, not a browser session — but it still loads the
 * real user and applies their account status and authorities, so server-side RBAC is unchanged.
 * An invalid or missing token leaves the context unauthenticated, so the chain's entry point
 * returns 401.
 */
@RequiredArgsConstructor
@Slf4j
public class McpTokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
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

                if (!jwtService.isTokenValid(jwt, userDetails, JwtService.TYPE_MCP)) {
                    log.debug("Invalid MCP token for user: {}", username);
                    filterChain.doFilter(request, response);
                    return;
                }

                // Block locked/disabled users from acting through the agent.
                accountStatusChecker.check(userDetails);

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Authenticated MCP agent for user: {}", username);
            }
        } catch (AccountStatusException e) {
            log.info("Blocking MCP request — {}", e.getMessage());
        } catch (Exception e) {
            log.error("MCP token authentication error: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
