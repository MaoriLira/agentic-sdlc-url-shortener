package com.example.urlshortener.util;

import java.util.regex.Pattern;

/**
 * Masking helpers for values that are safe to log in some form but not in full — see
 * URL-601 / docs/Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy.md.
 *
 * Secrets (API keys, password-equivalents) are NOT handled here: those are never logged in
 * any form, masked or not, so there is no "mask an API key" method. Masking is only for
 * values like IP addresses, where a partial value is legitimately useful for debugging and
 * doesn't reconstitute the original.
 */
public final class LogSanitizer {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private LogSanitizer() {
    }

    /**
     * Masks the last octet of an IPv4 address ({@code 203.0.113.42 -> 203.0.113.xxx}).
     * Non-IPv4 input (IPv6, hostnames, already-masked values) is returned unchanged rather
     * than guessed at.
     */
    public static String maskIp(String ip) {
        if (ip == null) {
            return null;
        }
        var matcher = IPV4.matcher(ip.trim());
        if (!matcher.matches()) {
            return ip;
        }
        return matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) + ".xxx";
    }
}
