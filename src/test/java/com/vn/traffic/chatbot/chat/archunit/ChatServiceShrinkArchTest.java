package com.vn.traffic.chatbot.chat.archunit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCH-01 guard — enforces the Phase 9 {@code ChatService.doAnswer} shrink
 * contract: all retrieval + prompt-build + citation-map identifiers must be
 * gone from {@code ChatService.java} once Task 4 lands.
 */
class ChatServiceShrinkArchTest {

    private static final List<String> DELETED_IDENTIFIERS = List.of(
            "vectorStore",
            "similaritySearch",
            "citationMapper.toCitations",
            "chatPromptFactory",
            "buildPrompt",
            "ChatPromptFactory"
    );

    @Test
    void chatServiceNoLongerReferencesVectorStoreSimilaritySearch() throws Exception {
        String chatService = Files.readString(
                Paths.get("src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java"));
        for (String id : DELETED_IDENTIFIERS) {
            assertThat(chatService)
                    .as("ChatService.java must not contain deleted identifier '%s' (ARCH-01 shrink)", id)
                    .doesNotContain(id);
        }
    }
}
