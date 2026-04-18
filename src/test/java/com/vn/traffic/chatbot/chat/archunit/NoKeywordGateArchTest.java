package com.vn.traffic.chatbot.chat.archunit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCH-03 guard — enforces the Phase-8 deletion contract that no
 * Vietnamese traffic-law keyword literal (from the P7-era
 * {@code containsLegalSignal} 14-keyword list) remains anywhere under
 * {@code src/main/java/com/vn/traffic/chatbot/} EXCEPT the {@code chat/intent/}
 * package (IntentClassifier's system prompt legitimately enumerates legal
 * vocabulary for the LLM).
 *
 * <p>Keywords are scanned as quoted string literals only (e.g. {@code "nghị định"})
 * to avoid false positives from javadoc / package comments.
 */
class NoKeywordGateArchTest {

    // 14 keywords lifted verbatim from ChatService.containsLegalSignal (lines 284-298
    // of the pre-08-03 file) before the method was deleted in Plan 08-03 Task 2.
    private static final List<String> P7_KEYWORD_LIST = List.of(
            "nghị định",
            "luật",
            "thông tư",
            "nghị quyết",
            "quy chuẩn",
            "quy định",
            "điều ",
            "khoản ",
            "điểm ",
            "xử phạt",
            "giao thông",
            "đường bộ",
            "biển số",
            "giấy phép lái xe",
            "đăng ký xe"
    );

    @Test
    void noVietnameseKeywordLiteralsOutsideIntentPackage() throws Exception {
        Path mainRoot = Paths.get("src/main/java/com/vn/traffic/chatbot");
        try (Stream<Path> walk = Files.walk(mainRoot)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/chat/intent/"))
                    .forEach(p -> {
                        String content;
                        try {
                            content = Files.readString(p);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to read " + p, e);
                        }
                        for (String kw : P7_KEYWORD_LIST) {
                            assertThat(content)
                                    .as("File %s must not contain P7 keyword literal '\"%s\"' (ARCH-03)", p, kw)
                                    .doesNotContain("\"" + kw + "\"");
                        }
                    });
        }
    }
}
