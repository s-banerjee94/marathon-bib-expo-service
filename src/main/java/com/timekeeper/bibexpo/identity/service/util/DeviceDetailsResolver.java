package com.timekeeper.bibexpo.identity.service.util;

import com.timekeeper.bibexpo.identity.model.DeviceDetails;
import com.timekeeper.bibexpo.identity.model.enums.DeviceType;
import com.timekeeper.bibexpo.shared.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the device details of a login request: its IP address, and the browser, operating
 * system and form factor reported by the client.
 *
 * <p>The User-Agent is the primary source because it carries version numbers; client hints
 * ({@code Sec-CH-UA-Platform}, {@code Sec-CH-UA-Mobile}) are consulted only where the header is
 * silent, since the low-entropy hints Chromium sends by default carry no versions. Every field is
 * best-effort and may come back null — the raw User-Agent is always stored alongside them so an
 * unrecognised client stays diagnosable.
 */
public final class DeviceDetailsResolver {

    /** Matches the storage width of {@code active_sessions.user_agent}. */
    public static final int USER_AGENT_MAX_LENGTH = 512;

    private static final Pattern EDGE = Pattern.compile("Edg(?:A|iOS)?/([\\d.]+)");
    private static final Pattern LEGACY_EDGE = Pattern.compile("Edge/([\\d.]+)");
    private static final Pattern OPERA = Pattern.compile("(?:OPR|Opera)[ /]([\\d.]+)");
    private static final Pattern SAMSUNG = Pattern.compile("SamsungBrowser/([\\d.]+)");
    private static final Pattern FIREFOX = Pattern.compile("(?:FxiOS|Firefox)/([\\d.]+)");
    private static final Pattern CHROME = Pattern.compile("(?:CriOS|Chrome)/([\\d.]+)");
    private static final Pattern SAFARI = Pattern.compile("Version/([\\d.]+).*Safari");

    private static final Pattern WINDOWS = Pattern.compile("Windows NT ([\\d.]+)");
    private static final Pattern ANDROID = Pattern.compile("Android ([\\d.]+)");
    private static final Pattern IOS = Pattern.compile("(?:iPhone OS|CPU OS) (\\d+[\\d_]*)");
    private static final Pattern MAC = Pattern.compile("Mac OS X (\\d+[\\d_]*)");

    private DeviceDetailsResolver() {
    }

    /**
     * Resolves every device field available from the request.
     *
     * @param request the login request, may be null
     * @return the details, never null, though individual fields may be
     */
    public static DeviceDetails resolve(HttpServletRequest request) {
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        String ua = userAgent == null ? "" : userAgent;

        return DeviceDetails.builder()
                .ipAddress(ClientIpResolver.resolve(request))
                .browser(browser(ua))
                .operatingSystem(operatingSystem(ua, header(request, "Sec-CH-UA-Platform")))
                .deviceType(deviceType(ua, header(request, "Sec-CH-UA-Mobile")))
                .userAgent(truncate(userAgent, USER_AGENT_MAX_LENGTH))
                .build();
    }

    private static String browser(String ua) {
        if (ua.isBlank()) return null;

        // Edge, Opera and Samsung Internet all carry a Chrome token too, so they must be tried
        // first; Safari carries no product token of its own and is what remains.
        String named = match(EDGE, ua, "Edge");
        if (named == null) named = match(LEGACY_EDGE, ua, "Edge");
        if (named == null) named = match(OPERA, ua, "Opera");
        if (named == null) named = match(SAMSUNG, ua, "Samsung Internet");
        if (named == null) named = match(FIREFOX, ua, "Firefox");
        if (named == null) named = match(CHROME, ua, "Chrome");
        if (named == null) named = match(SAFARI, ua, "Safari");
        return named != null ? named : nonBrowserClient(ua);
    }

    private static String operatingSystem(String ua, String platformHint) {
        if (!ua.isBlank()) {
            Matcher windows = WINDOWS.matcher(ua);
            if (windows.find()) return windowsName(windows.group(1));

            // Android reports Linux and iPads report Mac OS X, so both must be tried first.
            Matcher android = ANDROID.matcher(ua);
            if (android.find()) return "Android " + android.group(1);

            Matcher ios = IOS.matcher(ua);
            if (ios.find()) return "iOS " + ios.group(1).replace('_', '.');

            Matcher mac = MAC.matcher(ua);
            if (mac.find()) return "macOS " + mac.group(1).replace('_', '.');

            if (ua.contains("Macintosh")) return "macOS";
            if (ua.contains("CrOS")) return "ChromeOS";
            if (ua.contains("Linux") || ua.contains("X11")) return "Linux";
        }
        return unquote(platformHint);
    }

    private static DeviceType deviceType(String ua, String mobileHint) {
        if (ua.contains("iPad") || ua.contains("Tablet")
                || (ua.contains("Android") && !ua.contains("Mobile"))) {
            return DeviceType.TABLET;
        }
        if (ua.contains("Mobile") || ua.contains("iPhone") || ua.contains("iPod")) {
            return DeviceType.MOBILE;
        }
        if (!ua.isBlank()) return DeviceType.DESKTOP;
        return "?1".equals(mobileHint) ? DeviceType.MOBILE : DeviceType.UNKNOWN;
    }

    private static String windowsName(String ntVersion) {
        return switch (ntVersion) {
            // The User-Agent freezes at 10.0 for both, and telling them apart needs a
            // high-entropy hint the browser only sends when asked for it.
            case "10.0" -> "Windows 10/11";
            case "6.3" -> "Windows 8.1";
            case "6.2" -> "Windows 8";
            case "6.1" -> "Windows 7";
            case "6.0" -> "Windows Vista";
            case "5.1", "5.2" -> "Windows XP";
            default -> "Windows";
        };
    }

    /** A scripted client such as curl or Postman: its own product token beats reporting nothing. */
    private static String nonBrowserClient(String ua) {
        if (ua.startsWith("Mozilla/")) return null;
        int slash = ua.indexOf('/');
        return truncate(slash > 0 ? ua.substring(0, slash) : ua, 40);
    }

    private static String match(Pattern pattern, String ua, String name) {
        Matcher matcher = pattern.matcher(ua);
        if (!matcher.find()) return null;
        String version = matcher.group(1);
        int dot = version.indexOf('.');
        return name + " " + (dot > 0 ? version.substring(0, dot) : version);
    }

    private static String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    private static String unquote(String value) {
        if (value == null || value.isBlank()) return null;
        return value.replace("\"", "").trim();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
