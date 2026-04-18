package com.vn.traffic.chatbot.chat.service;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for G4 (Plan 09-04): SPRING_AI_CHAT_MEMORY.conversation_id is
 * VARCHAR(36). The former "ephemeral-" + UUID path produced a 46-char value and
 * triggered "value too long for type character varying(36)" on every
 * null-conversationId call.
 */
class ChatServiceEphemeralConversationIdTest {

    private static final Pattern UUID_REGEX =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @Test
    void nullCaller_producesBare36CharUuid() {
        String result = ChatService.buildMemoryConversationId(null);
        assertThat(result).hasSize(36);
        assertThat(result).doesNotStartWith("ephemeral-");
        assertThat(UUID_REGEX.matcher(result).matches()).isTrue();
    }

    @Test
    void blankCaller_producesBare36CharUuid() {
        String result = ChatService.buildMemoryConversationId("  ");
        assertThat(result).hasSize(36);
        assertThat(result).doesNotStartWith("ephemeral-");
        assertThat(UUID_REGEX.matcher(result).matches()).isTrue();
    }

    @Test
    void emptyCaller_producesBare36CharUuid() {
        String result = ChatService.buildMemoryConversationId("");
        assertThat(result).hasSize(36);
        assertThat(UUID_REGEX.matcher(result).matches()).isTrue();
    }

    @Test
    void callerSuppliedId_passesThroughUnchanged() {
        String result = ChatService.buildMemoryConversationId("abc-123");
        assertThat(result).isEqualTo("abc-123");
    }

    @Test
    void generatedIdsAreUnique() {
        String a = ChatService.buildMemoryConversationId(null);
        String b = ChatService.buildMemoryConversationId(null);
        assertThat(a).isNotEqualTo(b);
    }
}
