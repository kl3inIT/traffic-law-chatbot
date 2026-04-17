package com.vn.traffic.chatbot.ai.embedding;

/**
 * Normalization + hashing helper for embedding cache keys (CACHE-02b / D-15).
 *
 * <p>Wave-0 RED stub. Both methods throw {@link UnsupportedOperationException}.
 * Plan 07-02 Task 1 will replace the bodies with the real NFC + lowercase + trim +
 * SHA-256 implementation (diacritics preserved — NO accent stripping).
 */
public final class CacheKeyNormalizer {

    private CacheKeyNormalizer() {
        // utility class — no instances
    }

    /**
     * Normalize cache-key input text: {@code Normalizer.Form.NFC} → lowercase
     * (Locale.ROOT) → trim. Diacritics are preserved.
     *
     * @param s raw input text (must be non-null)
     * @return normalized form suitable for hashing into a cache key
     */
    public static String normalize(String s) {
        throw new UnsupportedOperationException("Wave 1 — not implemented");
    }

    /**
     * SHA-256 hex digest of the input string.
     *
     * @param s input string
     * @return 64-character lowercase hex string
     */
    public static String sha256(String s) {
        throw new UnsupportedOperationException("Wave 1 — not implemented");
    }
}
