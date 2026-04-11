package com.vn.traffic.chatbot.chat;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
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
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.chat.service.ChatThreadMapper;
import com.vn.traffic.chatbot.chat.service.ChatThreadService;
import com.vn.traffic.chatbot.chat.service.ClarificationPolicy;
import com.vn.traffic.chatbot.chat.service.FactMemoryService;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.chat.service.ScenarioAnswerComposer;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.parameter.repo.AiParameterSetRepository;
import com.vn.traffic.chatbot.parameter.service.ActiveParameterSetProvider;
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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatThreadFlowIntegrationTest {

    @Mock private ChatThreadRepository chatThreadRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ThreadFactRepository threadFactRepository;
    @Mock private ChatService chatService;

    private ChatThreadService chatThreadService;

    @BeforeEach
    void setUp() {
        AiParameterSetRepository paramRepo = org.mockito.Mockito.mock(AiParameterSetRepository.class);
        org.mockito.Mockito.lenient().when(paramRepo.findByActiveTrue()).thenReturn(java.util.Optional.empty());
        ActiveParameterSetProvider paramProvider = new ActiveParameterSetProvider(paramRepo);
        FactMemoryService factMemoryService = new FactMemoryService(threadFactRepository);
        ClarificationPolicy clarificationPolicy = new ClarificationPolicy(paramProvider);
        chatThreadService = new ChatThreadService(
                chatThreadRepository,
                chatMessageRepository,
                threadFactRepository,
                chatService,
                new ChatThreadMapper(new ScenarioAnswerComposer()),
                factMemoryService,
                clarificationPolicy
        );
    }

    @Test
    void materiallyIncompleteFirstTurnReturnsClarificationNeeded() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = ChatThread.builder().id(threadId).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        ChatMessage savedUser = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi vượt đèn đỏ thì bị phạt sao?").build();

        when(chatThreadRepository.save(any(ChatThread.class))).thenReturn(thread);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedUser);
        when(threadFactRepository.findFirstByThreadIdAndFactKeyAndStatusOrderByCreatedAtDesc(any(), any(), any())).thenReturn(Optional.empty());
        when(threadFactRepository.save(any(ThreadFact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(threadFactRepository.findByThreadIdAndStatusOrderByCreatedAtAsc(threadId, ThreadFactStatus.ACTIVE))
                .thenReturn(List.of(ThreadFact.builder().factKey("violationType").factValue("vượt đèn đỏ").status(ThreadFactStatus.ACTIVE).build()));

        ChatAnswerResponse response = chatThreadService.createThread("Tôi vượt đèn đỏ thì bị phạt sao?");

        assertThat(response.threadId()).isEqualTo(threadId);
        assertThat(response.responseMode()).isEqualTo(ResponseMode.CLARIFICATION_NEEDED);
        assertThat(response.pendingFacts()).isNotEmpty();
        assertThat(response.conclusion()).isNull();
    }

    @Test
    void answeringPendingFactMovesThreadToScenarioAnalysis() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = ChatThread.builder().id(threadId).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        ChatMessage savedUser = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi đi xe máy vượt đèn đỏ.").build();
        List<ThreadFact> activeFacts = List.of(
                ThreadFact.builder().factKey("vehicleType").factValue("xe máy").status(ThreadFactStatus.ACTIVE).build(),
                ThreadFact.builder().factKey("violationType").factValue("vượt đèn đỏ").status(ThreadFactStatus.ACTIVE).build()
        );

        when(chatThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedUser);
        when(threadFactRepository.findFirstByThreadIdAndFactKeyAndStatusOrderByCreatedAtDesc(any(), any(), any())).thenReturn(Optional.empty());
        when(threadFactRepository.save(any(ThreadFact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(threadFactRepository.findByThreadIdAndStatusOrderByCreatedAtAsc(threadId, ThreadFactStatus.ACTIVE)).thenReturn(activeFacts);
        when(chatMessageRepository.findByThreadIdOrderByCreatedAtAsc(threadId)).thenReturn(List.of(savedUser));
        when(chatService.answer(org.mockito.ArgumentMatchers.contains("vehicleType: xe máy"))).thenReturn(answer(threadId));

        ChatAnswerResponse response = chatThreadService.postMessage(threadId, "Tôi đi xe máy vượt đèn đỏ.");

        assertThat(response.responseMode()).isEqualTo(ResponseMode.FINAL_ANALYSIS);
        assertThat(response.rememberedFacts()).extracting(r -> r.key()).contains("vehicleType", "violationType");
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
                List.of(),
                List.of(),
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
