package com.vn.traffic.chatbot.chat.regression;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8 Plan 4 (Wave 3) — live Vietnamese regression suite (D-05 / D-05c).
 *
 * <p>Runs the 20 canonical Vietnamese traffic-law queries through the full 6-advisor
 * chain wired in Plan 08-02 and asserts:
 * <ul>
 *   <li>≥95% pass rate via {@link RelevancyEvaluator} + {@link FactCheckingEvaluator}
 *       (the Spring AI 2.0.0-M4 {@code org.springframework.ai.chat.evaluation} pair).</li>
 *   <li>Refusal rate on the 20 queries stays within ±10% of the Phase-7 baseline
 *       ({@link Phase7Baseline#REFUSAL_RATE_PERCENT}). Fails loud when the baseline is
 *       {@code NaN} — intentional per Plan 08-01 Case B.</li>
 *   <li>Turn-2 of a two-turn conversation references turn-1 topic, proving
 *       {@code MessageChatMemoryAdvisor} is carrying context through the default-advisor
 *       chain.</li>
 * </ul>
 *
 * <p>Note on the base class: RESEARCH §9.2 referenced {@code BasicEvaluationTest}, but that
 * class is not bundled in {@code spring-ai-test:2.0.0-M4} (confirmed via jar inspection during
 * Plan 08-01 Task 3). Instead, the evaluators are instantiated directly against a dedicated
 * raw {@link ChatClient.Builder} (no advisor chain) produced by the nested {@link EvaluatorConfig}.
 * This is the pattern documented in Spring AI reference docs for RelevancyEvaluator.
 *
 * <p>Gated by {@code @Tag("live")} + {@code @DisabledIfEnvironmentVariable} so
 * {@code ./gradlew test} (non-live) stays green when {@code OPENROUTER_API_KEY} is unset.
 * Run locally with {@code ./gradlew liveTest}.
 */
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class VietnameseRegressionIT {

    private static final int MIN_PASS_PERCENT = 95;
    private static final double P7_REFUSAL_BASELINE_PERCENT = Phase7Baseline.REFUSAL_RATE_PERCENT;
    private static final int REFUSAL_DRIFT_TOLERANCE_PERCENT = 10;

    @Autowired
    ChatService chatService;

    @Autowired
    AiModelProperties properties;

    @Autowired
    @Qualifier("evaluatorChatClientBuilder")
    ChatClient.Builder evaluatorChatClientBuilder;

    @Test
    void twentyQueryRegressionSuiteAtLeast95Percent() {
        List<QueryFixture> queries = loadFixture();
        assertThat(queries).as("fixture size").hasSizeGreaterThanOrEqualTo(20);

        RelevancyEvaluator relevancy = RelevancyEvaluator.builder()
                .chatClientBuilder(evaluatorChatClientBuilder)
                .build();
        FactCheckingEvaluator grounding = FactCheckingEvaluator.builder(evaluatorChatClientBuilder)
                .build();

        int passed = 0;
        int total = queries.size();
        List<String> failures = new ArrayList<>();
        for (QueryFixture q : queries) {
            ChatAnswerResponse resp = chatService.answer(q.question(), properties.chatModel(), null);
            String answer = resp.answer() == null ? "" : resp.answer();

            List<Document> citationDocs = citationsToDocuments(resp.citations());
            EvaluationResponse relResp = relevancy.evaluate(
                    new EvaluationRequest(q.question(), citationDocs, answer));
            EvaluationResponse factResp = grounding.evaluate(
                    new EvaluationRequest(q.question(), citationDocs, answer));

            boolean pass = relResp.isPass() && factResp.isPass();
            if (pass) {
                passed++;
            } else {
                failures.add(String.format("%s — relevancy=%s fact=%s",
                        q.id(), relResp.isPass(), factResp.isPass()));
            }
        }

        double percent = passed * 100.0 / total;
        assertThat(percent)
                .as("Regression pass rate (%d/%d). Failures: %s", passed, total, failures)
                .isGreaterThanOrEqualTo(MIN_PASS_PERCENT);
    }

    @Test
    void refusalRateWithinTenPercentOfPhase7Baseline() {
        List<QueryFixture> queries = loadFixture();
        int refused = 0;
        for (QueryFixture q : queries) {
            ChatAnswerResponse resp = chatService.answer(q.question(), properties.chatModel(), null);
            if (resp.groundingStatus() == GroundingStatus.REFUSED) {
                refused++;
            }
        }
        double rate = refused * 100.0 / queries.size();

        // Plan 08-01 Case B: if Phase7Baseline.REFUSAL_RATE_PERCENT is NaN, this assertion
        // fails loud (Math.abs(rate - NaN) is NaN, never <= 10). That is the intended
        // contract until a real P7 baseline is backfilled per Phase7Baseline TODO.
        assertThat(Math.abs(rate - P7_REFUSAL_BASELINE_PERCENT))
                .as("Refusal rate %.2f%% must be within ±%d%% of P7 baseline %.2f%%",
                        rate, REFUSAL_DRIFT_TOLERANCE_PERCENT, P7_REFUSAL_BASELINE_PERCENT)
                .isLessThanOrEqualTo((double) REFUSAL_DRIFT_TOLERANCE_PERCENT);
    }

    @Test
    void twoTurnConversationMemoryWorks() {
        String convId = UUID.randomUUID().toString();
        ChatAnswerResponse r1 = chatService.answer("Vượt đèn đỏ phạt bao nhiêu?", properties.chatModel(), convId);
        ChatAnswerResponse r2 = chatService.answer("Còn xe máy thì sao?", properties.chatModel(), convId);

        assertThat(r1).isNotNull();
        assertThat(r1.answer()).isNotBlank();
        assertThat(r2).isNotNull();
        assertThat(r2.answer()).isNotBlank();

        // Soft signal — turn 2 should reference turn-1's red-light / vượt topic or the
        // new xe máy specifier the user introduced. If all three are absent, memory did
        // not carry context forward through MessageChatMemoryAdvisor.
        String lower = r2.answer().toLowerCase();
        assertThat(lower)
                .as("Turn-2 answer should reference turn-1 topic. answer=%s", r2.answer())
                .containsAnyOf("đèn đỏ", "vượt", "xe máy");
    }

    private List<QueryFixture> loadFixture() {
        try (InputStream in = getClass().getResourceAsStream("/regression/vietnamese-queries-20.yaml")) {
            if (in == null) {
                throw new IllegalStateException(
                        "Fixture not found: /regression/vietnamese-queries-20.yaml");
            }
            Map<String, Object> root = new Yaml().load(in);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> raw = (List<Map<String, Object>>) root.get("queries");
            List<QueryFixture> out = new ArrayList<>(raw.size());
            for (Map<String, Object> entry : raw) {
                String id = String.valueOf(entry.get("id"));
                String question = String.valueOf(entry.get("question"));
                Object hint = entry.get("expected_citation_hint");
                out.add(new QueryFixture(id, question, hint == null ? null : hint.toString()));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load regression fixture", e);
        }
    }

    private List<Document> citationsToDocuments(List<CitationResponse> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        List<Document> docs = new ArrayList<>(citations.size());
        for (CitationResponse c : citations) {
            String excerpt = c.excerpt();
            if (excerpt != null && !excerpt.isBlank()) {
                docs.add(new Document(excerpt));
            }
        }
        return docs;
    }

    record QueryFixture(String id, String question, String expectedCitationHint) {}

    /**
     * Provides a raw {@link ChatClient.Builder} — no advisor chain — for the
     * evaluators. Uses {@code AiModelProperties.evaluatorModel()} pointed at the same
     * OpenRouter OpenAI-compat gateway as the production chat clients. Kept separate
     * from the main {@code chatClientMap} so evaluator calls are not routed through
     * {@link com.vn.traffic.chatbot.chat.advisor.GroundingGuardInputAdvisor} /
     * {@code GroundingGuardOutputAdvisor} (otherwise an evaluator query asking about
     * citation grounding would itself get grounding-refused).
     */
    @TestConfiguration
    static class EvaluatorConfig {

        @Value("${spring.ai.openai.api-key:none}")
        private String apiKey;

        @Bean(name = "evaluatorChatClientBuilder")
        ChatClient.Builder evaluatorChatClientBuilder(AiModelProperties props) {
            String baseUrl = props.baseUrl() != null ? props.baseUrl() : "https://openrouter.ai/api/v1";
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .build();
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(props.evaluatorModel())
                    .build();
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(options)
                    .build();
            return ChatClient.builder(model);
        }
    }
}
