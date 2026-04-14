package com.vn.traffic.chatbot.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for the {@code app.*} namespace.
 *
 * <p>Registered via {@code @EnableConfigurationProperties(AppProperties.class)} on
 * {@link PropertiesConfig}. Do NOT annotate this class with {@code @Configuration}
 * or {@code @Component} — use {@code @EnableConfigurationProperties} pattern only.
 *
 * <p>Centralizes all {@code app.*} YAML keys so that CorsConfig, ChatService, and
 * other consumers can inject AppProperties instead of scattered {@code @Value} fields.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();
    private Chat chat = new Chat();

    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }

    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat; }

    // -----------------------------------------------------------------------
    // Nested classes
    // -----------------------------------------------------------------------

    public static class Cors {
        private String allowedOrigins = "http://localhost:3000";

        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Chat {
        private Retrieval retrieval = new Retrieval();
        private Grounding grounding = new Grounding();

        public Retrieval getRetrieval() { return retrieval; }
        public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }

        public Grounding getGrounding() { return grounding; }
        public void setGrounding(Grounding grounding) { this.grounding = grounding; }

        public static class Retrieval {
            private int topK = 5;

            public int getTopK() { return topK; }
            public void setTopK(int topK) { this.topK = topK; }
        }

        public static class Grounding {
            private int limitedThreshold = 2;

            public int getLimitedThreshold() { return limitedThreshold; }
            public void setLimitedThreshold(int limitedThreshold) { this.limitedThreshold = limitedThreshold; }
        }
    }
}
