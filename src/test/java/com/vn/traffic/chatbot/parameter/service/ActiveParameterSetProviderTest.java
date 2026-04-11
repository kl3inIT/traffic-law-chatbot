package com.vn.traffic.chatbot.parameter.service;

import com.vn.traffic.chatbot.parameter.domain.AiParameterSet;
import com.vn.traffic.chatbot.parameter.repo.AiParameterSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveParameterSetProviderTest {

    @Mock
    AiParameterSetRepository repository;

    ActiveParameterSetProvider provider;

    // Test YAML content:
    // model:
    //   temperature: 0.3
    // retrieval:
    //   topK: 5
    //   similarityThreshold: 0.7
    // caseAnalysis:
    //   maxClarifications: 2
    //   requiredFacts:
    //     - key: vehicleType
    //       alwaysRequired: true
    // messages:
    //   disclaimer: "Test disclaimer"
    private static final String TEST_YAML = """
            model:
              temperature: 0.3
            retrieval:
              topK: 5
              similarityThreshold: 0.7
            caseAnalysis:
              maxClarifications: 2
              requiredFacts:
                - key: vehicleType
                  alwaysRequired: true
            messages:
              disclaimer: "Test disclaimer"
            """;

    @BeforeEach
    void setUp() {
        provider = new ActiveParameterSetProvider(repository);
    }

    private AiParameterSet paramSetWithContent(String yamlContent) {
        return AiParameterSet.builder()
                .id(UUID.randomUUID())
                .name("Test Set")
                .content(yamlContent)
                .active(true)
                .build();
    }

    @Test
    void getString_nestedPath_returnsValue() {
        when(repository.findByActiveTrue()).thenReturn(Optional.of(paramSetWithContent(TEST_YAML)));

        String result = provider.getString("messages.disclaimer", "fallback");

        assertThat(result).isEqualTo("Test disclaimer");
    }

    @Test
    void getString_missingPath_returnsFallback() {
        when(repository.findByActiveTrue()).thenReturn(Optional.of(paramSetWithContent(TEST_YAML)));

        String result = provider.getString("nonexistent.path", "fallback");

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void getInt_nestedPath_returnsValue() {
        when(repository.findByActiveTrue()).thenReturn(Optional.of(paramSetWithContent(TEST_YAML)));

        int result = provider.getInt("caseAnalysis.maxClarifications", 99);

        assertThat(result).isEqualTo(2);
    }

    @Test
    void getInt_missingPath_returnsFallback() {
        when(repository.findByActiveTrue()).thenReturn(Optional.of(paramSetWithContent(TEST_YAML)));

        int result = provider.getInt("missing.key", 42);

        assertThat(result).isEqualTo(42);
    }

    @Test
    void getDouble_nestedPath_returnsValue() {
        when(repository.findByActiveTrue()).thenReturn(Optional.of(paramSetWithContent(TEST_YAML)));

        double result = provider.getDouble("retrieval.similarityThreshold", 0.5);

        assertThat(result).isEqualTo(0.7);
    }

    @Test
    void getDouble_missingPath_returnsFallback() {
        when(repository.findByActiveTrue()).thenReturn(Optional.of(paramSetWithContent(TEST_YAML)));

        double result = provider.getDouble("missing.path", 0.9);

        assertThat(result).isEqualTo(0.9);
    }

    @Test
    void getList_nestedPath_returnsList() {
        when(repository.findByActiveTrue()).thenReturn(Optional.of(paramSetWithContent(TEST_YAML)));

        List<Map<String, Object>> result = provider.getList("caseAnalysis.requiredFacts");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("key", "vehicleType");
        assertThat(result.get(0)).containsEntry("alwaysRequired", true);
    }

    @Test
    void getActiveParams_noActiveSet_returnsEmptyMap() {
        when(repository.findByActiveTrue()).thenReturn(Optional.empty());

        Map<String, Object> result = provider.getActiveParams();

        assertThat(result).isEmpty();
    }
}
