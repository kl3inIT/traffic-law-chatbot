package com.vn.traffic.chatbot.chat.intent;

import com.vn.traffic.chatbot.chat.config.ChatClientConfig;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 09-05 (G5 close-out): proves {@link IntentClassifier} is wired to the
 * sibling {@link ChatClientConfig#intentChatClientMap(AiModelProperties)} bean —
 * NOT the default {@code chatClientMap} that carries the
 * {@code LegalAnswerDraft}-bound {@code StructuredOutputValidationAdvisor}.
 *
 * <p>Verifies (at the reflection level, without booting Spring) that the
 * injected field is qualified with {@code @Qualifier("intentChatClientMap")}.
 * A full-context IT lives in {@code IntentClassifierIT}.
 */
class IntentClassifierWiringTest {

    @Test
    void chatClientMapFieldIsQualifiedForIntentMap() throws NoSuchFieldException {
        Field field = IntentClassifier.class.getDeclaredField("chatClientMap");
        org.springframework.beans.factory.annotation.Qualifier qualifier =
                field.getAnnotation(org.springframework.beans.factory.annotation.Qualifier.class);
        assertThat(qualifier)
                .as("IntentClassifier.chatClientMap must carry @Qualifier to avoid routing through the LegalAnswerDraft-bound main chain")
                .isNotNull();
        assertThat(qualifier.value()).isEqualTo("intentChatClientMap");
    }

    @Test
    void intentClassifierAcceptsIntentMapByConstructor() {
        ChatClient stubClient = org.mockito.Mockito.mock(ChatClient.class);
        Map<String, ChatClient> intentMap = Map.of("m", stubClient);
        AiModelProperties props = new AiModelProperties(
                "http://localhost", "m", "m",
                List.of(new AiModelProperties.ModelEntry("m", "m", null, null, true)));

        IntentClassifier classifier = new IntentClassifier(intentMap, props);

        assertThat(classifier).isNotNull();
    }
}
