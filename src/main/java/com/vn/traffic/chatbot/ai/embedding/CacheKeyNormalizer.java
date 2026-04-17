package com.vn.traffic.chatbot.ai.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Normalization + hashing helper for embedding cache keys (CACHE-02b / D-15).
 *
 * <p>Normalization = {@link Normalizer.Form#NFC} + {@link String#trim()} +
 * lowercase via {@link Locale#ROOT}. Vietnamese diacritics are PRESERVED
 * (no accent stripping) — locked by D-15 because diacritics are semantically
 * load-bearing for Vietnamese legal retrieval.
 *
 * <p>SHA-256 produces a 64-character lowercase hex digest of the UTF-8 bytes.
 */
public final class CacheKeyNormalizer {

    private CacheKeyNormalizer() {
        // utility class — no instances
    }

    /**
     * Normalize cache-key input text: {@code Normalizer.Form.NFC} → trim →
     * lowercase (Locale.ROOT). Diacritics are preserved.
     *
     * @param s raw input text
     * @return normalized form suitable for hashing into a cache key
     */
    public static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC)
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * SHA-256 hex digest of the input string (UTF-8 bytes).
     *
     * @param s input string
     * @return 64-character lowercase hex string
     */
    public static String sha256(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on JDK 25", e);
        }
    }
}
