package com.vn.traffic.chatbot.ai.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED test for {@link CacheKeyNormalizer} (CACHE-02b / D-15).
 *
 * <p>All assertions target the final Wave-1 behavior:
 * NFC + lowercase(Locale.ROOT) + trim, with diacritics preserved.
 * Today the stub throws {@link UnsupportedOperationException} — this test
 * is RED at runtime and goes GREEN when Plan 07-02 Task 1 ships.
 */
class CacheKeyNormalizerTest {

    @Test
    void lowercasesAsciiAndVietnamese() {
        String out = CacheKeyNormalizer.normalize("Vượt Đèn Đỏ");
        assertThat(out).isEqualTo("vượt đèn đỏ");
    }

    @Test
    void nfcNormalizesComposedVsDecomposed() {
        // "à" as pre-composed (U+00E0) vs "a\u0300" (a + combining grave U+0300)
        String composed = "\u00E0";
        String decomposed = "a\u0300";
        assertThat(CacheKeyNormalizer.normalize(composed))
                .isEqualTo(CacheKeyNormalizer.normalize(decomposed));
    }

    @Test
    void trimsLeadingTrailingWhitespace() {
        assertThat(CacheKeyNormalizer.normalize("  xin chào  ")).isEqualTo("xin chào");
    }

    @Test
    void preservesDiacritics() {
        String out = CacheKeyNormalizer.normalize("Hà Nội");
        assertThat(out).contains("à");
        assertThat(out).contains("ộ");
    }

    @Test
    void sha256ProducesHexString() {
        String hash = CacheKeyNormalizer.sha256(CacheKeyNormalizer.normalize("test"));
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
        // deterministic
        assertThat(hash).isEqualTo(CacheKeyNormalizer.sha256(CacheKeyNormalizer.normalize("test")));
    }
}
