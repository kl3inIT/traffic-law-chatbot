package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.ChatMessageResponse;
import com.vn.traffic.chatbot.chat.api.dto.ChatThreadSummaryResponse;
import com.vn.traffic.chatbot.chat.domain.ChatMessage;
import com.vn.traffic.chatbot.chat.domain.ChatMessageRole;
import com.vn.traffic.chatbot.chat.domain.ChatMessageType;
import com.vn.traffic.chatbot.chat.domain.ChatThread;
import com.vn.traffic.chatbot.chat.repo.ChatMessageRepository;
import com.vn.traffic.chatbot.chat.repo.ChatThreadRepository;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatThreadService {

    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatService chatService;
    private final ChatThreadMapper chatThreadMapper;

    @Transactional
    public ChatAnswerResponse createThread(String question) {
        ChatThread thread = chatThreadRepository.save(ChatThread.builder().build());
        appendUserMessage(thread, question);
        ChatAnswerResponse answer = chatService.answer(question, null, thread.getId().toString());
        appendAssistantMessage(thread, answer);
        return chatThreadMapper.attachScenarioContext(answer, thread.getId(), answer.sources());
    }

    @Transactional
    public ChatAnswerResponse postMessage(UUID threadId, String question) {
        ChatThread thread = chatThreadRepository.findById(threadId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_THREAD_NOT_FOUND, "Chat thread not found: " + threadId));
        appendUserMessage(thread, question);
        ChatAnswerResponse answer = chatService.answer(question, null, threadId.toString());
        appendAssistantMessage(thread, answer);
        return chatThreadMapper.attachScenarioContext(answer, thread.getId(), answer.sources());
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
}
