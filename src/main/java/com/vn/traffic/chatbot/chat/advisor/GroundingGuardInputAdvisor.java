package com.vn.traffic.chatbot.chat.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Pre-call advisor for the grounding-guard pair (ARCH-04).
 *
 * <p>In Phase 8 this is a thin pass-through — Plan 08-03 ChatService writes the
 * {@link #FORCE_REFUSAL} key into the request context BEFORE the chain executes,
 * and Phase 9 extends this advisor with trust-tier preflight checks on
 * retrieval filter expressions.
 *
 * <p>Ordered at {@code HIGHEST_PRECEDENCE + 100} so it runs before
 * {@code MessageChatMemoryAdvisor} ({@code HIGHEST_PRECEDENCE + 200}).
 *
 * <p>Pitfall 4 (RESEARCH §8): never mutate {@link ChatClientRequest} in place;
 * use {@code req.mutate()....build()} + {@code chain.nextCall(mutatedReq)} when
 * a branch needs to modify the request.
 */
@Slf4j
@Component
public final class GroundingGuardInputAdvisor implements CallAdvisor {

    /**
     * Context key that Plan 08-03 ChatService writes into
     * {@link ChatClientRequest#context()} to force the refusal path.
     */
    public static final String FORCE_REFUSAL = "chat.guard.forceRefusal";

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    @Override
    public String getName() {
        return "GroundingGuardInputAdvisor";
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
