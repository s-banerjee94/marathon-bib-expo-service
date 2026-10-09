package com.timekeeper.bibexpo.bootstrap;

import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates MDC with per-request context so structured log lines carry tracing fields.
 * Registered inside the security filter chain (after JWT authentication) so the
 * authenticated principal is available.
 */
public class MdcRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
            MDC.put("method", request.getMethod());
            MDC.put("uri", request.getRequestURI());

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User user) {
                MDC.put("userId", String.valueOf(user.getId()));
                MDC.put("username", user.getUsername());
                MDC.put("role", user.getRole().name());
                if (user.getOrganization() != null) {
                    MDC.put("orgId", String.valueOf(user.getOrganization().getId()));
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
