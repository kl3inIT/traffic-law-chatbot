package com.vn.traffic.chatbot.chatlog.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.chatlog.domain.ChatLog;
import com.vn.traffic.chatbot.chatlog.repo.ChatLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatLogService {

    private final ChatLogRepository chatLogRepository;

    public void save(String question, ChatAnswerResponse response, GroundingStatus groundingStatus,
                     String conversationId, int promptTokens, int completionTokens, int responseTime,
                     String pipelineLog) {
        ChatLog chatLog = ChatLog.builder()
                .question(question)
                .answer(response.answer())
                .sources(buildSourcesString(response))
                .groundingStatus(groundingStatus)
                .conversationId(conversationId)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .responseTime(responseTime)
                .pipelineLog(pipelineLog)
                .build();
        chatLogRepository.save(chatLog);
    }

    private String buildSourcesString(ChatAnswerResponse response) {
        if (response.sources() == null) return null;
        return response.sources().stream()
                .map(s -> s.sourceTitle() != null ? s.sourceTitle() : s.origin())
                .collect(Collectors.joining(", "));
    }

    public Page<ChatLog> findAll(Pageable pageable) {
        return chatLogRepository.findAll(pageable);
    }

    public Optional<ChatLog> findById(UUID id) {
        return chatLogRepository.findById(id);
    }

    public Page<ChatLog> findFiltered(GroundingStatus groundingStatus,
                                       OffsetDateTime from, OffsetDateTime to,
                                       String q, Pageable pageable) {
        Specification<ChatLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (groundingStatus != null) {
                predicates.add(cb.equal(root.get("groundingStatus"), groundingStatus));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdDate"), to));
            }
            if (q != null && !q.isBlank()) {
                String escaped = q.toLowerCase()
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_");
                predicates.add(
                    cb.like(cb.lower(root.get("question")),
                            cb.literal("%" + escaped + "%"), '!')
                );
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return chatLogRepository.findAll(spec, pageable);
    }
}
