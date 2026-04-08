package com.vn.traffic.chatbot.chat.api;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.ChatQuestionRequest;
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.CHAT)
@RequiredArgsConstructor
public class PublicChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatAnswerResponse> answer(@Valid @RequestBody ChatQuestionRequest request) {
        return ResponseEntity.ok(chatService.answer(request.question()));
    }
}
