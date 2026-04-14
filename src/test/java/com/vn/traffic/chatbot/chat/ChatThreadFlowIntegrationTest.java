package com.vn.traffic.chatbot.chat;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.domain.ChatMessage;
import com.vn.traffic.chatbot.chat.domain.ChatMessageRole;
import com.vn.traffic.chatbot.chat.domain.ChatMessageType;
import com.vn.traffic.chatbot.chat.domain.ChatThread;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import com.vn.traffic.chatbot.chat.repo.ChatMessageRepository;
import com.vn.traffic.chatbot.chat.repo.ChatThreadRepository;
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.chat.service.ChatThreadMapper;
import com.vn.traffic.chatbot.chat.service.ChatThreadService;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.chat.service.ScenarioAnswerComposer;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatThreadFlowIntegrationTest {

    @Mock private ChatThreadRepository chatThreadRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatService chatService;

    private ChatThreadService chatThreadService;

    @BeforeEach
    void setUp() {
        chatThreadService = new ChatThreadService(
                chatThreadRepository,
                chatMessageRepository,
                chatService,
                new ChatThreadMapper(new ScenarioAnswerComposer())
        );
    }

    @Test
    void createThreadReturnsGroundedAnswer() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = ChatThread.builder().id(threadId).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        ChatMessage savedUser = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Xe máy vượt đèn đỏ bị phạt sao?").build();

        when(chatThreadRepository.save(any(ChatThread.class))).thenReturn(thread);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedUser);
        when(chatService.answer(eq("Xe máy vượt đèn đỏ bị phạt sao?"), isNull(), anyString())).thenReturn(answer(threadId));

        ChatAnswerResponse response = chatThreadService.createThread("Xe máy vượt đèn đỏ bị phạt sao?");

        assertThat(response.threadId()).isEqualTo(threadId);
        assertThat(response.conclusion()).isNotNull();
    }

    @Test
    void postMessageReturnsFinalAnalysisWithScenario() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = ChatThread.builder().id(threadId).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        ChatMessage savedUser = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi đi xe máy vượt đèn đỏ.").build();

        when(chatThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedUser);
        when(chatService.answer(eq("Tôi đi xe máy vượt đèn đỏ."), isNull(), anyString())).thenReturn(answer(threadId));

        ChatAnswerResponse response = chatThreadService.postMessage(threadId, "Tôi đi xe máy vượt đèn đỏ.");

        assertThat(response.responseMode()).isEqualTo(ResponseMode.FINAL_ANALYSIS);
        assertThat(response.scenarioAnalysis()).isNotNull();
    }

    @Test
    void unknownThreadRaisesDomainSpecificNotFoundError() {
        UUID threadId = UUID.randomUUID();
        when(chatThreadRepository.findById(threadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatThreadService.postMessage(threadId, "Tiếp tục phân tích"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(threadId.toString())
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_THREAD_NOT_FOUND);
    }

    private ChatAnswerResponse answer(UUID threadId) {
        return new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                threadId,
                ResponseMode.STANDARD,
                "Nội dung trả lời",
                "Kết luận",
                "Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.",
                null,
                List.of("Điều 7 [Nguồn 1]"),
                List.of("Phạt tiền [Nguồn 1]"),
                List.of(),
                List.of(),
                List.of("Đối chiếu biên bản"),
                List.of("Người điều khiển dùng xe máy", "Hành vi: vượt đèn đỏ"),
                new com.vn.traffic.chatbot.chat.api.dto.ScenarioAnalysisResponse(
                        List.of("Người điều khiển dùng xe máy", "Hành vi: vượt đèn đỏ"),
                        "Áp dụng Điều 7 [Nguồn 1]",
                        "Có thể bị xử phạt theo khung tương ứng [Nguồn 1]",
                        List.of("Giữ lại biên bản để đối chiếu"),
                        List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"))
                ),
                List.of(new CitationResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7", "Trích dẫn")),
                List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"))
        );
    }
}
