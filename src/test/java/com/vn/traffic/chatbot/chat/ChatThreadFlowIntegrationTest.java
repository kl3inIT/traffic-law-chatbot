package com.vn.traffic.chatbot.chat;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.domain.ChatMessage;
import com.vn.traffic.chatbot.chat.domain.ChatMessageRole;
import com.vn.traffic.chatbot.chat.domain.ChatMessageType;
import com.vn.traffic.chatbot.chat.domain.ChatThread;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import com.vn.traffic.chatbot.chat.domain.ThreadFactStatus;
import com.vn.traffic.chatbot.chat.repo.ChatMessageRepository;
import com.vn.traffic.chatbot.chat.repo.ChatThreadRepository;
import com.vn.traffic.chatbot.chat.repo.ThreadFactRepository;
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.chat.service.ChatThreadMapper;
import com.vn.traffic.chatbot.chat.service.ChatThreadService;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatThreadFlowIntegrationTest {

    @Mock
    private ChatThreadRepository chatThreadRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ThreadFactRepository threadFactRepository;
    @Mock
    private ChatService chatService;

    private ChatThreadService chatThreadService;

    @BeforeEach
    void setUp() {
        chatThreadService = new ChatThreadService(
                chatThreadRepository,
                chatMessageRepository,
                threadFactRepository,
                chatService,
                new ChatThreadMapper()
        );
    }

    @Test
    void threadContinuityUsesSameThreadIdentityAcrossTurns() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = ChatThread.builder()
                .id(threadId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(chatThreadRepository.save(any(ChatThread.class))).thenReturn(thread);
        when(chatService.answer("Tôi vượt đèn đỏ bằng xe máy thì sao?"))
                .thenReturn(answer(threadId));
        when(chatThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(threadFactRepository.findByThreadIdAndStatusOrderByCreatedAtAsc(threadId, ThreadFactStatus.ACTIVE))
                .thenReturn(List.of());
        when(chatService.answer("Nếu tôi gây tai nạn thì mức phạt đổi thế nào?"))
                .thenReturn(answer(threadId));

        ChatAnswerResponse created = chatThreadService.createThread("Tôi vượt đèn đỏ bằng xe máy thì sao?");
        ChatAnswerResponse continued = chatThreadService.postMessage(threadId, "Nếu tôi gây tai nạn thì mức phạt đổi thế nào?");

        assertThat(created.threadId()).isEqualTo(threadId);
        assertThat(continued.threadId()).isEqualTo(threadId);
        assertThat(created.responseMode()).isEqualTo(ResponseMode.STANDARD);
        assertThat(continued.responseMode()).isEqualTo(ResponseMode.STANDARD);
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
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
                List.of(),
                List.of(),
                null,
                List.of(new CitationResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7", "Trích dẫn")),
                List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"))
        );
    }
}
