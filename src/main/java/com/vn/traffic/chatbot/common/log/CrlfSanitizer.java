package com.vn.traffic.chatbot.common.log;

/**
 * Utility class for preventing CRLF log injection attacks.
 *
 * <p>Security (T-4.1-01-03): User-supplied strings (title, url) logged in
 * IngestionAdminController must be sanitized before reaching the log output.
 * CR (\r) and LF (\n) characters are replaced with underscores to neutralize
 * injection payloads that could forge additional log lines.
 */
public final class CrlfSanitizer {

    private CrlfSanitizer() {
        // static utility — not instantiable
    }

    /**
     * Sanitizes the given value by replacing all carriage-return ({@code \r})
     * and line-feed ({@code \n}) characters with {@code _}.
     *
     * @param value the string to sanitize; may be null
     * @return the sanitized string, or the literal {@code "null"} if value is null
     */
    public static String sanitize(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace('\r', '_').replace('\n', '_');
    }
}
