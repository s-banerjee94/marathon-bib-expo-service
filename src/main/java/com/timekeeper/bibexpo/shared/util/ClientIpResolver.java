package com.timekeeper.bibexpo.shared.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the caller's originating IP address from a servlet request.
 *
 * <p>In production the application sits behind nginx, so the socket address is always the proxy's
 * own loopback. The first {@code X-Forwarded-For} hop is the client the proxy accepted, and is
 * used whenever the header is present; the socket address is the fallback for direct calls.
 */
public final class ClientIpResolver {

    /** Longest possible textual IPv6 address, which is what the stored columns are sized for. */
    public static final int MAX_LENGTH = 45;

    private ClientIpResolver() {
    }

    /**
     * Returns the caller's IP address: the first {@code X-Forwarded-For} hop when behind the
     * reverse proxy, otherwise the socket address.
     *
     * @param request the current servlet request, may be null
     * @return the resolved address, or null when the request is null or carries no address
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) return null;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String firstHop = forwarded.split(",")[0].trim();
            if (!firstHop.isEmpty()) return truncate(firstHop);
        }
        return truncate(request.getRemoteAddr());
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }
}
