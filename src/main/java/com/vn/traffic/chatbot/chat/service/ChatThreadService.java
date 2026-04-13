package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.ChatMessageResponse;
import com.vn.traffic.chatbot.chat.api.dto.ChatThreadSummaryResponse;
import com.vn.traffic.chatbot.chat.api.dto.PendingFactResponse;
import com.vn.traffic.chatbot.chat.domain.ChatMessage;
import com.vn.traffic.chatbot.chat.domain.ChatMessageRole;
import com.vn.traffic.chatbot.chat.domain.ChatMessageType;
import com.vn.traffic.chatbot.chat.domain.ChatThread;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import com.vn.traffic.chatbot.chat.domain.ThreadFact;
import com.vn.traffic.chatbot.chat.domain.ThreadFactStatus;
import com.vn.traffic.chatbot.chat.repo.ChatMessageRepository;
import com.vn.traffic.chatbot.chat.repo.ChatThreadRepository;
import com.vn.traffic.chatbot.chat.repo.ThreadFactRepository;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatThreadService {

    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ThreadFactRepository threadFactRepository;
    private final ChatService chatService;
    private final ChatThreadMapper chatThreadMapper;
    private final FactMemoryService factMemoryService;
    private final LlmClarificationService llmClarificationService;

    @Transactional
    public ChatAnswerResponse createThread(String question) {
        ChatThread thread = chatThreadRepository.save(ChatThread.builder().build());
        ChatMessage userMessage = appendUserMessage(thread, question);
        factMemoryService.rememberExplicitFacts(thread, userMessage);
        List<ThreadFact> activeFacts = factMemoryService.getActiveFacts(thread);
        Map<String, String> factMap = toFactMap(activeFacts);
        ClarificationPolicy.ClarificationDecision decision = llmClarificationService.decide(question, factMap, 0);
        if (decision.clarificationNeeded()) {
            return chatThreadMapper.attachThreadContext(
                    clarificationResponse(thread, decision.pendingFacts()),
                    thread.getId(),
                    ResponseMode.CLARIFICATION_NEEDED,
                    activeFacts
            );
        }
        ChatAnswerResponse answer = chatService.answer(factMemoryService.buildThreadAwareQuestion(question, activeFacts));
        appendAssistantMessage(thread, answer);
        return chatThreadMapper.attachScenarioContext(answer, thread.getId(), activeFacts, answer.sources());
    }

    @Transactional
    public ChatAnswerResponse postMessage(UUID threadId, String question) {
        ChatThread thread = chatThreadRepository.findById(threadId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_THREAD_NOT_FOUND, "Chat thread not found: " + threadId));
        ChatMessage userMessage = appendUserMessage(thread, question);
        factMemoryService.rememberExplicitFacts(thread, userMessage);
        List<ThreadFact> activeFacts = threadFactRepository.findByThreadIdAndStatusOrderByCreatedAtAsc(
                threadId, ThreadFactStatus.ACTIVE);
        int clarificationCount = countClarificationMessages(threadId);
        Map<String, String> factMap = toFactMap(activeFacts);
        ClarificationPolicy.ClarificationDecision decision = llmClarificationService.decide(question, factMap, clarificationCount);
        if (decision.clarificationNeeded()) {
            return chatThreadMapper.attachThreadContext(
                    clarificationResponse(thread, decision.pendingFacts()),
                    thread.getId(),
                    ResponseMode.CLARIFICATION_NEEDED,
                    activeFacts
            );
        }
        String retrievalQuestion = buildRetrievalQuestion(threadId, question, activeFacts);
        ChatAnswerResponse answer = chatService.answer(retrievalQuestion);
        appendAssistantMessage(thread, answer);
        return chatThreadMapper.attachScenarioContext(answer, thread.getId(), activeFacts, answer.sources());
    }

    @Transactional(readOnly = true)
    public ChatThread getThread(UUID threadId) {
        return chatThreadRepository.findById(threadId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_THREAD_NOT_FOUND, "Chat thread not found: " + threadId));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID threadId) {
        chatThreadRepository.findById(threadId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_THREAD_NOT_FOUND, "Chat thread not found: " + threadId));
        return chatMessageRepository.findByThreadIdOrderByCreatedAtAsc(threadId).stream()
                .map(m -> new ChatMessageResponse(m.getId(), m.getRole(), m.getMessageType(), m.getContent(), m.getCreatedAt(), m.getStructuredResponse()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatThreadSummaryResponse> listThreads() {
        return chatThreadRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(thread -> {
                    String firstMsg = chatMessageRepository.findByThreadIdOrderByCreatedAtAsc(thread.getId())
                            .stream()
                            .filter(m -> m.getRole() == ChatMessageRole.USER)
                            .map(ChatMessage::getContent)
                            .findFirst()
                            .orElse(null);
                    String truncated = firstMsg != null && firstMsg.length() > 100
                            ? firstMsg.substring(0, 100) : firstMsg;
                    return new ChatThreadSummaryResponse(
                            thread.getId(), thread.getCreatedAt(), thread.getUpdatedAt(), truncated);
                })
                .toList();
    }

    private Map<String, String> toFactMap(List<ThreadFact> facts) {
        return facts.stream().collect(
                Collectors.toMap(ThreadFact::getFactKey, ThreadFact::getFactValue, (left, right) -> right));
    }

    private ChatMessage appendUserMessage(ChatThread thread, String question) {
        return chatMessageRepository.save(ChatMessage.builder()
                .thread(thread)
                .role(ChatMessageRole.USER)
                .messageType(ChatMessageType.QUESTION)
                .content(question)
                .build());
    }

    private ChatMessage appendAssistantMessage(ChatThread thread, ChatAnswerResponse response) {
        return chatMessageRepository.save(ChatMessage.builder()
                .thread(thread)
                .role(ChatMessageRole.ASSISTANT)
                .messageType(ChatMessageType.ANSWER)
                .content(response.answer() != null ? response.answer() : "")
                .structuredResponse(response)
                .build());
    }

    private int countClarificationMessages(UUID threadId) {
        return (int) chatMessageRepository.findByThreadIdOrderByCreatedAtAsc(threadId).stream()
                .filter(m -> m.getRole() == ChatMessageRole.ASSISTANT)
                .filter(m -> m.getMessageType() == ChatMessageType.CLARIFICATION)
                .count();
    }

    private String buildRetrievalQuestion(UUID threadId, String currentQuestion, List<ThreadFact> activeFacts) {
        String originalQuestion = chatMessageRepository.findByThreadIdOrderByCreatedAtAsc(threadId).stream()
                .filter(m -> m.getRole() == ChatMessageRole.USER)
                .map(ChatMessage::getContent)
                .findFirst()
                .orElse(currentQuestion);
        return factMemoryService.buildThreadAwareQuestion(originalQuestion, activeFacts);
    }

    private ChatAnswerResponse clarificationResponse(ChatThread thread, List<PendingFactResponse> pendingFacts) {
        ChatAnswerResponse response = new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                thread.getId(),
                ResponseMode.CLARIFICATION_NEEDED,
                "[CLARIFICATION] Tôi cần làm rõ thêm một số tình tiết trước khi kết luận.",
                null,
                AnswerCompositionPolicy.DEFAULT_DISCLAIMER,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Vui lòng trả lời trực tiếp các câu hỏi làm rõ để tôi phân tích đúng căn cứ."),
                pendingFacts,
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of()
        );
        chatMessageRepository.save(ChatMessage.builder()
                .thread(thread)
                .role(ChatMessageRole.ASSISTANT)
                .messageType(ChatMessageType.CLARIFICATION)
                .content(response.answer())
                .structuredResponse(response)
                .build());
        return response;
    }
}
