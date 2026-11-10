package com.edu360.api.selfservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * SES-01 helpers for describing a signed-in device from what the browser sends at login.
 *
 * Deliberately small and dependency-free: the result is display text for the user's own
 * "My sessions" list ("Chrome on Windows"), not a security decision - nothing is granted or
 * denied on the strength of a User-Agent, which the client controls anyway.
 *
 * device_class is limited by the schema to PHONE or COMPUTER; tablets count as PHONE (a
 * handheld device), which is the more useful grouping for "where am I signed in".
 */
public final class DeviceDescriptor {

    public static final String PHONE = "PHONE";
    public static final String COMPUTER = "COMPUTER";

    /** The client-generated browser id: a UUID in practice; anything else is ignored. */
    private static final Pattern BROWSER_KEY = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");
    private static final Pattern MOBILE = Pattern.compile("(?i)mobi|iphone|ipod|ipad|android|tablet");

    private DeviceDescriptor() {
    }

    public static String deviceClass(String userAgent) {
        return userAgent != null && MOBILE.matcher(userAgent).find() ? PHONE : COMPUTER;
    }

    /** "Browser on OS", e.g. "Edge on Windows", "Safari on iPhone"; null when nothing is known. */
    public static String label(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String browser = browser(userAgent);
        String os = os(userAgent);
        String label;
        if (browser != null && os != null) {
            label = browser + " on " + os;
        } else if (browser != null) {
            label = browser;
        } else if (os != null) {
            label = os;
        } else {
            return null;
        }
        return label.length() > 100 ? label.substring(0, 100) : label;
    }

    /** SHA-256 hex of a well-formed browser key, or null when absent/malformed (the device row
     *  is then still recorded, it just cannot be matched to "the same browser" later). */
    public static String hashBrowserKey(String browserKey) {
        if (browserKey == null || !BROWSER_KEY.matcher(browserKey).matches()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(browserKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", ex);
        }
    }

    // Order matters: Edge, Opera and Samsung all also say "Chrome", and Chrome also says "Safari".
    private static String browser(String ua) {
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("SamsungBrowser/")) return "Samsung Internet";
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) return "Firefox";
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) return "Chrome";
        if (ua.contains("Safari/")) return "Safari";
        return null;
    }

    private static String os(String ua) {
        if (ua.contains("iPhone")) return "iPhone";
        if (ua.contains("iPad")) return "iPad";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("CrOS")) return "ChromeOS";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        return null;
    }
}
