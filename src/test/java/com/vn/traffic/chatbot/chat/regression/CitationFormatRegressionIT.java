package com.vn.traffic.chatbot.chat.regression;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * RED scaffold for Phase 9 Task 4 — v1.0 {@code ChatAnswerResponse}
 * fixture replay asserting byte-for-byte {@code [Nguồn n]} +
 * {@code citations[]} / {@code sources[]} parity (ARCH-05).
 */
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class CitationFormatRegressionIT {

    @Test
    void v1FixtureReplayPreservesCitationsAndSourcesByteForByte() {
        fail("RED — implement byte-for-byte fixture replay in Task 4");
    }
}
