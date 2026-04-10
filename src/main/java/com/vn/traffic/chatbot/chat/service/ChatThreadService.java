package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatThreadService {

    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ThreadFactRepository threadFactRepository;
    private final ChatService chatService;
    private final ChatThreadMapper chatThreadMapper;

    @Transactional
    public ChatAnswerResponse createThread(String question) {
        ChatThread thread = chatThreadRepository.save(ChatThread.builder().build());
        appendUserMessage(thread, question);
        ChatAnswerResponse answer = chatService.answer(question);
        return chatThreadMapper.attachThreadContext(answer, thread.getId(), ResponseMode.STANDARD, List.of());
    }

    @Transactional
    public ChatAnswerResponse postMessage(UUID threadId, String question) {
        ChatThread thread = chatThreadRepository.findById(threadId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_THREAD_NOT_FOUND, "Chat thread not found: " + threadId));
        appendUserMessage(thread, question);
        List<ThreadFact> rememberedFacts = threadFactRepository.findByThreadIdAndStatusOrderByCreatedAtAsc(
                threadId,
                ThreadFactStatus.ACTIVE
        );
        ChatAnswerResponse answer = chatService.answer(question);
        return chatThreadMapper.attachThreadContext(answer, thread.getId(), ResponseMode.STANDARD, rememberedFacts);
    }

    @Transactional(readOnly = true)
    public ChatThread getThread(UUID threadId) {
        return chatThreadRepository.findById(threadId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_THREAD_NOT_FOUND, "Chat thread not found: " + threadId));
    }

    private void appendUserMessage(ChatThread thread, String question) {
        chatMessageRepository.save(ChatMessage.builder()
                .thread(thread)
                .role(ChatMessageRole.USER)
                .messageType(ChatMessageType.QUESTION)
                .content(question)
                .build());
    }
}
