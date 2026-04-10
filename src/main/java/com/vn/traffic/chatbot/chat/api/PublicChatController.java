package com.vn.traffic.chatbot.chat.api;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.ChatQuestionRequest;
import com.vn.traffic.chatbot.chat.api.dto.ChatThreadMessageRequest;
import com.vn.traffic.chatbot.chat.api.dto.CreateChatThreadRequest;
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.chat.service.ChatThreadService;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.CHAT)
@RequiredArgsConstructor
public class PublicChatController {

    private final ChatService chatService;
    private final ChatThreadService chatThreadService;

    @PostMapping
    public ResponseEntity<ChatAnswerResponse> answer(@Valid @RequestBody ChatQuestionRequest request) {
        return ResponseEntity.ok(chatService.answer(request.question()));
    }

    @PostMapping("/threads")
    public ResponseEntity<ChatAnswerResponse> createThread(@Valid @RequestBody CreateChatThreadRequest request) {
        return ResponseEntity.ok(chatThreadService.createThread(request.question()));
    }

    @PostMapping("/threads/{threadId}/messages")
    public ResponseEntity<ChatAnswerResponse> postMessage(
            @PathVariable UUID threadId,
            @Valid @RequestBody ChatThreadMessageRequest request
    ) {
        return ResponseEntity.ok(chatThreadService.postMessage(threadId, request.question()));
    }
}
