package com.vn.traffic.chatbot.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 0 — RED test for AppProperties typed configuration binding.
 * Verifies that app.* YAML values bind correctly to the AppProperties bean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration," +
                "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class AppPropertiesTest {

    @Autowired
    private AppProperties appProperties;

    @Test
    void chatRetrievalTopKBindsToDefault() {
        assertThat(appProperties.getChat().getRetrieval().getTopK())
                .as("app.chat.retrieval.top-k should bind to 5 from application.yaml")
                .isEqualTo(5);
    }

    @Test
    void chatGroundingLimitedThresholdBindsToDefault() {
        assertThat(appProperties.getChat().getGrounding().getLimitedThreshold())
                .as("app.chat.grounding.limited-threshold should bind to 2 from application.yaml")
                .isEqualTo(2);
    }

    @Test
    void chatCaseAnalysisMaxClarificationsBindsToDefault() {
        assertThat(appProperties.getChat().getCaseAnalysis().getMaxClarifications())
                .as("app.chat.case-analysis.max-clarifications should bind to 2 from application.yaml")
                .isEqualTo(2);
    }

    @Test
    void corsAllowedOriginsBindsToDefault() {
        assertThat(appProperties.getCors().getAllowedOrigins())
                .as("app.cors.allowed-origins should bind to http://localhost:3000 by default")
                .isEqualTo("http://localhost:3000");
    }
}
