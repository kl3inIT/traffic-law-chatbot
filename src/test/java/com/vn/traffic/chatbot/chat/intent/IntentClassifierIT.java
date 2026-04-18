package com.vn.traffic.chatbot.chat.intent;

import com.vn.traffic.chatbot.common.config.AiModelProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration tests for {@link IntentClassifier} (D-01, D-09).
 *
 * <p>Plan 08-04 Wave 3 — exercises the real OpenRouter model on three intent
 * classes (LEGAL, CHITCHAT, OFF_TOPIC) to validate ARCH-03 end-to-end. Unit
 * coverage of the D-02 fail-LEGAL policy lives in {@code IntentClassifierTest}.
 *
 * <p>CI skips this class via the default {@code test} task's {@code excludeTags 'live'}
 * filter and {@code @DisabledIfEnvironmentVariable} when {@code OPENROUTER_API_KEY}
 * is blank. Run locally with: {@code ./gradlew liveTest}.
 */
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class IntentClassifierIT {

    @Autowired
    IntentClassifier classifier;

    @Autowired
    AiModelProperties properties;

    @Test
    void legalQuestionClassifiedAsLegal() {
        IntentDecision decision = classifier.classify(
                "Vượt đèn đỏ đối với xe máy bị phạt bao nhiêu tiền?",
                properties.chatModel());

        assertThat(decision).isNotNull();
        assertThat(decision.intent()).isEqualTo(IntentDecision.Intent.LEGAL);
        assertThat(decision.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void greetingClassifiedAsChitchat() {
        IntentDecision decision = classifier.classify(
                "Xin chào!",
                properties.chatModel());

        assertThat(decision).isNotNull();
        // A plain greeting must not fall back to LEGAL (the D-02 fail-safe value)
        // and must not be classified OFF_TOPIC either — it's classic chitchat.
        assertThat(decision.intent()).isEqualTo(IntentDecision.Intent.CHITCHAT);
    }

    @Test
    void offTopicQuestionClassifiedAsOffTopic() {
        IntentDecision decision = classifier.classify(
                "Giá Bitcoin hôm nay là bao nhiêu?",
                properties.chatModel());

        assertThat(decision).isNotNull();
        assertThat(decision.intent()).isEqualTo(IntentDecision.Intent.OFF_TOPIC);
    }
}
