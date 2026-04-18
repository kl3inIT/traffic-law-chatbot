package com.vn.traffic.chatbot.chat.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9 Task 3 — advisor bean-graph assertion: real
 * {@code RetrievalAugmentationAdvisor} is wired in place of
 * {@code NoOpRetrievalAdvisor}; {@code CitationStashAdvisor} publishes
 * citations/sources; {@code NoOpPromptCacheAdvisor} is preserved (D-07).
 *
 * <p>Scaffolded as RED in Task 1 — uses bean-name lookups (string-based) so it
 * compiles before the {@code CitationStashAdvisor} / real advisor classes land
 * in Tasks 2/3. Green-gate flips when Task 3 wires the real beans.
 *
 * <p>This test replaces the P8 unit-style {@code chatClientMap(...)} direct-call
 * assertions (which pinned the {@code NoOpRetrievalAdvisor} parameter type);
 * those are subsumed by Spring-context bean-graph checks below after the P9 swap.
 */
@SpringBootTest
class ChatClientConfigTest {

    @Autowired
    ApplicationContext ctx;

    @Test
    void retrievalAugmentationAdvisorBeanIsRegisteredAtOrderHighestPrecedencePlus300() throws Exception {
        Class<?> raaType = Class.forName("org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor");
        String[] names = ctx.getBeanNamesForType(raaType);
        assertThat(names)
                .as("RetrievalAugmentationAdvisor bean must be registered (P9 Task 3)")
                .isNotEmpty();
        Object advisor = ctx.getBean(raaType);
        int order = (int) advisor.getClass().getMethod("getOrder").invoke(advisor);
        assertThat(order)
                .as("Retrieval advisor order slot must equal HIGHEST_PRECEDENCE + 300 (D-13)")
                .isEqualTo(Integer.MIN_VALUE + 300);
    }

    @Test
    void citationStashAdvisorBeanExistsExactlyOnceAtOrder310() throws Exception {
        Class<?> stashType = Class.forName("com.vn.traffic.chatbot.chat.advisor.CitationStashAdvisor");
        assertThat(ctx.getBeansOfType(stashType)).hasSize(1);
        Object advisor = ctx.getBean(stashType);
        int order = (int) advisor.getClass().getMethod("getOrder").invoke(advisor);
        assertThat(order)
                .as("CitationStashAdvisor must slot between RAG (+300) and NoOpPromptCache (+500)")
                .isEqualTo(Integer.MIN_VALUE + 310);
    }

    @Test
    void noOpRetrievalAdvisorBeanIsGoneAndPromptCachePlaceholderIsPreserved() {
        assertThat(ctx.containsBean("noOpRetrievalAdvisor"))
                .as("NoOpRetrievalAdvisor bean must be deleted in P9 PR1")
                .isFalse();
        assertThat(ctx.containsBean("noOpPromptCacheAdvisor"))
                .as("NoOpPromptCacheAdvisor must remain (D-07)")
                .isTrue();
    }
}
