package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AnswerComposer {

    private final AnswerCompositionPolicy policy;

    /**
     * Compose a canned chitchat/greeting response that bypasses retrieval and LLM (D-02).
     * Used for one-off greetings like "xin chào" to keep p50 low.
     */
    public ChatAnswerResponse composeChitchat() {
        String canned = "Xin chào! Tôi là trợ lý hỏi đáp pháp luật giao thông Việt Nam. "
                + "Bạn hãy đặt câu hỏi cụ thể về luật giao thông (ví dụ: mức phạt, thủ tục, giấy tờ) để tôi có thể tra cứu và trả lời kèm nguồn.";
        return new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                null,
                ResponseMode.STANDARD,
                canned,
                null,
                policy.getDisclaimer(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of()
        );
    }

    public ChatAnswerResponse compose(
            GroundingStatus groundingStatus,
            LegalAnswerDraft draft,
            List<CitationResponse> citations,
            List<SourceReferenceResponse> sources
    ) {
        if (groundingStatus == GroundingStatus.REFUSED) {
            List<String> nextSteps = policy.getRefusalNextSteps();
            return new ChatAnswerResponse(
                    GroundingStatus.REFUSED,
                    null,
                    ResponseMode.STANDARD,
                    buildAnswer(null, List.of(), List.of(), List.of(), List.of(), nextSteps),
                    null,
                    policy.getDisclaimer(),
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    nextSteps,
                    List.of(),
                    null,
                    List.of(),
                    List.of()
            );
        }

        LegalAnswerDraft safeDraft = draft == null
                ? new LegalAnswerDraft(null, null, null, List.of(), List.of(), List.of(), List.of(), List.of())
                : draft;

        List<String> legalBasis = safeList(safeDraft.legalBasis());
        List<String> penalties = safeList(safeDraft.penalties());
        List<String> requiredDocuments = safeList(safeDraft.requiredDocuments());
        List<String> procedureSteps = safeList(safeDraft.procedureSteps());
        List<String> nextSteps = safeList(safeDraft.nextSteps());
        List<CitationResponse> safeCitations = safeList(citations);
        List<SourceReferenceResponse> safeSources = safeList(sources);
        String uncertaintyNotice = hasText(safeDraft.uncertaintyNotice())
                ? safeDraft.uncertaintyNotice().trim()
                : null;
        String normalizedConclusion = normalizeConclusion(safeDraft.conclusion());

        String answer = buildAnswer(normalizedConclusion, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps);

        return new ChatAnswerResponse(
                groundingStatus,
                null,
                ResponseMode.STANDARD,
                answer,
                normalizedConclusion,
                policy.getDisclaimer(),
                uncertaintyNotice,
                legalBasis,
                penalties,
                requiredDocuments,
                procedureSteps,
                nextSteps,
                List.of(),
                null,
                safeCitations,
                safeSources
        );
    }

    private String buildAnswer(
            String conclusion,
            List<String> legalBasis,
            List<String> penalties,
            List<String> requiredDocuments,
            List<String> procedureSteps,
            List<String> nextSteps
    ) {
        List<String> sections = new ArrayList<>();

        if (hasText(conclusion)) {
            sections.add("Kết luận:\n" + conclusion.trim());
        }
        addSection(sections, "Căn cứ pháp lý", legalBasis);
        addSection(sections, "Mức phạt hoặc hậu quả", penalties);
        addSection(sections, "Giấy tờ hoặc thủ tục", mergeSections(requiredDocuments, procedureSteps));
        addSection(sections, "Các bước nên làm tiếp", nextSteps);
        sections.add(policy.getDisclaimer());

        return String.join("\n\n", sections);
    }

    private void addSection(List<String> sections, String label, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        sections.add(label + ":\n- " + String.join("\n- ", items));
    }

    private List<String> mergeSections(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first);
        merged.addAll(second);
        return merged;
    }

    private String normalizeConclusion(String conclusion) {
        if (!hasText(conclusion)) {
            return conclusion;
        }
        String trimmed = conclusion.trim();
        if (trimmed.regionMatches(true, 0, "Kết luận:", 0, "Kết luận:".length())) {
            return trimmed.substring("Kết luận:".length()).trim();
        }
        return trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> safeList(List<T> value) {
        if (value == null) {
            return List.of();
        }
        return value.stream().filter(Objects::nonNull).toList();
    }
}
