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
 * Phase 9 placeholder slot for {@code PromptCachingAdvisor} (D-04).
 * Pass-through only: Phase 9 will inject OpenRouter {@code cache_control}
 * breakpoints on the static system block here.
 *
 * <p>Pitfall 6 (RESEARCH §8): pass-through contract — {@code chain.nextCall(req)}
 * only, no fresh response construction.
 */
@Slf4j
@Component
public final class NoOpPromptCacheAdvisor implements CallAdvisor {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 500;

    @Override
    public String getName() {
        return "NoOpPromptCacheAdvisor";
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
