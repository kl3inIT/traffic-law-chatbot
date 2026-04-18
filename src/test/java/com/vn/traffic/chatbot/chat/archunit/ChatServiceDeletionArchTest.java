package com.vn.traffic.chatbot.chat.archunit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCH-03 guard — enforces the Phase-8 deletion contract for
 * {@code ChatService.java}: all 9 P7-era identifiers (keyword heuristics,
 * manual JSON-parsing helpers, chitchat regex) must be gone from the file.
 *
 * <p>Fails CI if any deleted identifier re-emerges via copy-paste or merge
 * from a pre-08-03 branch.
 */
class ChatServiceDeletionArchTest {

    private static final List<String> DELETED_IDENTIFIERS = List.of(
            "parseDraft",
            "extractJson",
            "fallbackDraft",
            "CHITCHAT_PATTERN",
            "isGreetingOrChitchat",
            "containsAnyLegalCitation",
            "looksLikeLegalCitation",
            "containsLegalSignal",
            "hasLegalCitation"
    );

    @Test
    void chatServiceNoLongerContainsDeletedIdentifiers() throws Exception {
        String chatService = Files.readString(
                Paths.get("src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java"));
        for (String id : DELETED_IDENTIFIERS) {
            assertThat(chatService)
                    .as("ChatService.java must not contain deleted identifier '%s' (ARCH-03)", id)
                    .doesNotContain(id);
        }
    }
}
