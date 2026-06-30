package com.timekeeper.bibexpo.security;

import com.timekeeper.bibexpo.service.JwtService;
import com.timekeeper.bibexpo.service.SessionService;
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
 * <p>Accepts only the user's own {@code type=access} token, which the Python agent forwards
 * unchanged (browser → agent → MCP). The single-session {@code sid} check is enforced so a token
 * from a superseded login cannot keep acting. It loads the real user and applies their account
 * status and authorities, so server-side RBAC is unchanged. An invalid or missing token leaves the
 * context unauthenticated, so the chain's entry point returns 401.
 */
@RequiredArgsConstructor
@Slf4j
public class McpTokenAuthenticationFilter extends OncePerRequestFilter {

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

                // Only the user's own access token is accepted (the Python agent forwards it
                // unchanged). isTokenValid still checks signature, username and expiry.
                final String tokenType = jwtService.extractTokenType(jwt);
                if (!JwtService.TYPE_ACCESS.equals(tokenType)
                        || !jwtService.isTokenValid(jwt, userDetails, tokenType)) {
                    log.debug("Invalid agent token (type={}) for user: {}", tokenType, username);
                    filterChain.doFilter(request, response);
                    return;
                }

                // The access token carries a browser session id: honour single-session logout so a
                // token from a superseded login cannot keep acting.
                String tokenSid = jwtService.extractSid(jwt);
                if (tokenSid == null || !tokenSid.equals(sessionService.getActiveSid(username))) {
                    log.debug("Stale access token (sid mismatch) on MCP route for user: {}", username);
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
