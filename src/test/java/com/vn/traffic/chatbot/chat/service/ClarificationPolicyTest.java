package com.vn.traffic.chatbot.chat.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationPolicyTest {

    @Test
    void blocksFinalScenarioConclusionWhenMaterialFactsAreMissing() {
        assertThat("Clarification-needed policy contract not implemented yet").isBlank();
    }
}
