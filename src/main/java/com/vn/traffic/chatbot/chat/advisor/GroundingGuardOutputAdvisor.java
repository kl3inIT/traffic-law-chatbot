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
 * Post-call advisor for the grounding-guard pair (ARCH-04).
 *
 * <p>In Phase 8 this is a thin pass-through. Plan 08-03 ChatService consumes
 * the exposed {@link #REFUSAL_TEMPLATE} constant when composing grounding-
 * refusal responses. Phase 9 extends this advisor with a post-model grounding
 * validator that can rewrite the response to the refusal template when the
 * answer lacks trusted citations.
 *
 * <p>Ordered at {@code LOWEST_PRECEDENCE - 100} so it runs last in the chain,
 * after every no-op P9 placeholder slot.
 */
@Slf4j
@Component
public final class GroundingGuardOutputAdvisor implements CallAdvisor {

    /**
     * Refusal template reused by Plan 08-03 ChatService when grounding fails.
     * Constant-as-field per D-06 (no {@code @ConfigurationProperties}, no
     * runtime knobs). Vietnamese-first, mirrors
     * {@code ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK} style.
     */
    public static final String REFUSAL_TEMPLATE =
            "Tôi chỉ có thể trả lời các câu hỏi về luật giao thông Việt Nam dựa trên nguồn pháp luật tin cậy. " +
                    "Vui lòng đặt câu hỏi cụ thể về luật giao thông để tôi có thể hỗ trợ.";

    private static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    @Override
    public String getName() {
        return "GroundingGuardOutputAdvisor";
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
