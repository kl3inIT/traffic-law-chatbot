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
 * <p>Covers the GREEN path — unit coverage of the D-02 fail-LEGAL policy lives
 * in {@code IntentClassifierTest}. CI skips this class via the default
 * {@code test} task's {@code excludeTags 'live'} filter and
 * {@code @DisabledIfEnvironmentVariable} when {@code OPENROUTER_API_KEY} is
 * blank. Run locally with: {@code ./gradlew liveTest}.
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
    void entityIntentDecisionReturnsNonNull() {
        IntentDecision decision = classifier.classify(
                "Vượt đèn đỏ đối với xe máy bị phạt bao nhiêu tiền?",
                properties.chatModel());

        assertThat(decision).isNotNull();
        assertThat(decision.intent()).isEqualTo(IntentDecision.Intent.LEGAL);
        assertThat(decision.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void classifierChitchatIsNotLegal() {
        IntentDecision decision = classifier.classify(
                "Xin chào!",
                properties.chatModel());

        assertThat(decision).isNotNull();
        // Chitchat greeting must not be classified as LEGAL (the D-02 fallback value),
        // so even if the classifier is conservative, it must return CHITCHAT here.
        assertThat(decision.intent()).isEqualTo(IntentDecision.Intent.CHITCHAT);
    }
}
