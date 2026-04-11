package com.vn.traffic.chatbot.common.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 0 — RED test for CrlfSanitizer utility.
 * Verifies CR/LF replacement with underscore and null-safety.
 */
class CrlfSanitizerTest {

    @Test
    void sanitizesCarriageReturnAndLineFeedWithUnderscore() {
        String result = CrlfSanitizer.sanitize("hello\r\nworld");
        assertThat(result)
                .as("CRLF characters should be replaced by underscores")
                .isEqualTo("hello__world");
    }

    @Test
    void sanitizesCarriageReturnOnly() {
        String result = CrlfSanitizer.sanitize("line1\rline2");
        assertThat(result).isEqualTo("line1_line2");
    }

    @Test
    void sanitizesLineFeedOnly() {
        String result = CrlfSanitizer.sanitize("line1\nline2");
        assertThat(result).isEqualTo("line1_line2");
    }

    @Test
    void nullInputReturnsNullString() {
        String result = CrlfSanitizer.sanitize(null);
        assertThat(result)
                .as("null input should return the string literal 'null'")
                .isEqualTo("null");
    }

    @Test
    void cleanInputReturnedUnchanged() {
        String input = "https://vbpl.vn/pages/clean-url?id=12345";
        assertThat(CrlfSanitizer.sanitize(input)).isEqualTo(input);
    }

    @Test
    void emptyStringReturnedUnchanged() {
        assertThat(CrlfSanitizer.sanitize("")).isEqualTo("");
    }
}
