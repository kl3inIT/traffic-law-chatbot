package com.vn.traffic.chatbot.chat.advisor.placeholder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Phase 9 placeholder slot for {@code RetrievalAugmentationAdvisor} (D-04).
 * Pass-through only: Phase 8 retains the existing manual retrieval path in
 * {@code ChatService}; Phase 9 swaps this implementation in place without
 * re-ordering the chain.
 *
 * <p>Pitfall 6 (RESEARCH §8): {@code adviseCall} MUST invoke
 * {@code chain.nextCall(req)} and return its result unchanged — never
 * construct a fresh {@link ChatClientResponse}.
 */
@Slf4j
@Component
public final class NoOpRetrievalAdvisor implements CallAdvisor {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 300;

    @Override
    public String getName() {
        return "NoOpRetrievalAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        return chain.nextCall(req);
    }

    @PostConstruct
    void logOrder() {
        log.info("Advisor registered: {} order={}", getName(), ORDER);
    }
}
