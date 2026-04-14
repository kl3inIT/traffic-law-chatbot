package com.vn.traffic.chatbot.chatlog.api;

import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.chatlog.api.dto.ChatLogDetailResponse;
import com.vn.traffic.chatbot.chatlog.api.dto.ChatLogResponse;
import com.vn.traffic.chatbot.chatlog.service.ChatLogService;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.api.ResponseGeneral;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ChatLogAdminController {

    private final ChatLogService chatLogService;

    @GetMapping(ApiPaths.CHAT_LOGS)
    public ResponseGeneral<Page<ChatLogResponse>> list(
            @RequestParam(required = false) GroundingStatus groundingStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String q,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ChatLogResponse> page = chatLogService.findFiltered(groundingStatus, from, to, q, pageable)
                .map(ChatLogResponse::fromEntity);
        return ResponseGeneral.ofSuccess("Chat logs", page);
    }

    @GetMapping(ApiPaths.CHAT_LOG_BY_ID)
    public ResponseEntity<ResponseGeneral<ChatLogDetailResponse>> getById(@PathVariable UUID logId) {
        return chatLogService.findById(logId)
                .map(log -> ResponseEntity.ok(ResponseGeneral.ofSuccess("Chat log detail", ChatLogDetailResponse.fromEntity(log))))
                .orElse(ResponseEntity.notFound().build());
    }
}
