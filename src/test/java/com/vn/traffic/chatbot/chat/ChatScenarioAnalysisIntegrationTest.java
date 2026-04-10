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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatScenarioAnalysisIntegrationTest {

    @Mock private ChatThreadRepository chatThreadRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ThreadFactRepository threadFactRepository;
    @Mock private ChatService chatService;

    private ChatThreadService chatThreadService;

    @BeforeEach
    void setUp() {
        chatThreadService = new ChatThreadService(
                chatThreadRepository,
                chatMessageRepository,
                threadFactRepository,
                chatService,
                new ChatThreadMapper(new ScenarioAnswerComposer()),
                new FactMemoryService(threadFactRepository),
                new ClarificationPolicy(2)
        );
    }

    @Test
    void threadLifecycleMovesFromClarificationToFinalAnalysisWithSources() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = ChatThread.builder().id(threadId).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        ChatMessage firstUser = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi vượt đèn đỏ thì bị phạt sao?").build();
        ChatMessage secondUser = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi đi xe máy.").build();

        when(chatThreadRepository.save(any(ChatThread.class))).thenReturn(thread);
        when(chatThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(firstUser, secondUser, secondUser);
        when(threadFactRepository.findFirstByThreadIdAndFactKeyAndStatusOrderByCreatedAtDesc(any(), any(), any())).thenReturn(Optional.empty());
        when(threadFactRepository.save(any(ThreadFact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(threadFactRepository.findByThreadIdAndStatusOrderByCreatedAtAsc(threadId, ThreadFactStatus.ACTIVE))
                .thenReturn(List.of(ThreadFact.builder().factKey("violationType").factValue("vượt đèn đỏ").status(ThreadFactStatus.ACTIVE).build()))
                .thenReturn(List.of(
                        ThreadFact.builder().factKey("vehicleType").factValue("xe máy").status(ThreadFactStatus.ACTIVE).build(),
                        ThreadFact.builder().factKey("violationType").factValue("vượt đèn đỏ").status(ThreadFactStatus.ACTIVE).build()
                ));
        when(chatMessageRepository.findByThreadIdOrderByCreatedAtAsc(threadId)).thenReturn(List.of(firstUser));
        when(chatService.answer(org.mockito.ArgumentMatchers.contains("vehicleType: xe máy"))).thenReturn(finalAnswer());

        ChatAnswerResponse clarification = chatThreadService.createThread("Tôi vượt đèn đỏ thì bị phạt sao?");
        ChatAnswerResponse finalAnalysis = chatThreadService.postMessage(threadId, "Tôi đi xe máy.");

        assertThat(clarification.responseMode()).isEqualTo(ResponseMode.CLARIFICATION_NEEDED);
        assertThat(clarification.pendingFacts()).isNotEmpty();
        assertThat(finalAnalysis.responseMode()).isEqualTo(ResponseMode.FINAL_ANALYSIS);
        assertThat(finalAnalysis.threadId()).isEqualTo(threadId);
        assertThat(finalAnalysis.scenarioAnalysis()).isNotNull();
        assertThat(finalAnalysis.scenarioAnalysis().facts()).contains("vehicleType: xe máy", "violationType: vượt đèn đỏ");
        assertThat(finalAnalysis.scenarioAnalysis().rule()).contains("Điều 7");
        assertThat(finalAnalysis.scenarioAnalysis().outcome()).contains("xử phạt");
        assertThat(finalAnalysis.scenarioAnalysis().actions()).contains("Đối chiếu biên bản");
        assertThat(finalAnalysis.citations()).isNotEmpty();
        assertThat(finalAnalysis.sources()).isNotEmpty();
    }

    @Test
    void limitedGroundingAfterClarificationStillProducesFinalAnalysis() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = ChatThread.builder().id(threadId).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        ChatMessage firstUser = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi vượt đèn đỏ thì bị phạt sao?").build();
        ChatMessage secondUser = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi đi xe máy.").build();

        when(chatThreadRepository.save(any(ChatThread.class))).thenReturn(thread);
        when(chatThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(firstUser, secondUser, secondUser);
        when(threadFactRepository.findFirstByThreadIdAndFactKeyAndStatusOrderByCreatedAtDesc(any(), any(), any())).thenReturn(Optional.empty());
        when(threadFactRepository.save(any(ThreadFact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(threadFactRepository.findByThreadIdAndStatusOrderByCreatedAtAsc(threadId, ThreadFactStatus.ACTIVE))
                .thenReturn(List.of(ThreadFact.builder().factKey("violationType").factValue("vượt đèn đỏ").status(ThreadFactStatus.ACTIVE).build()))
                .thenReturn(List.of(
                        ThreadFact.builder().factKey("vehicleType").factValue("xe máy").status(ThreadFactStatus.ACTIVE).build(),
                        ThreadFact.builder().factKey("violationType").factValue("vượt đèn đỏ").status(ThreadFactStatus.ACTIVE).build()
                ));
        when(chatMessageRepository.findByThreadIdOrderByCreatedAtAsc(threadId)).thenReturn(List.of(firstUser));
        when(chatService.answer(org.mockito.ArgumentMatchers.contains("Tôi vượt đèn đỏ"))).thenReturn(limitedGroundingAnswer());

        ChatAnswerResponse clarification = chatThreadService.createThread("Tôi vượt đèn đỏ thì bị phạt sao?");
        ChatAnswerResponse finalAnalysis = chatThreadService.postMessage(threadId, "Tôi đi xe máy.");

        assertThat(clarification.responseMode()).isEqualTo(ResponseMode.CLARIFICATION_NEEDED);
        assertThat(finalAnalysis.responseMode()).isEqualTo(ResponseMode.FINAL_ANALYSIS);
        assertThat(finalAnalysis.scenarioAnalysis()).isNotNull();
        assertThat(finalAnalysis.scenarioAnalysis().facts()).isNotEmpty();
    }

    private ChatAnswerResponse limitedGroundingAnswer() {
        return new ChatAnswerResponse(
                GroundingStatus.LIMITED_GROUNDING,
                null,
                ResponseMode.STANDARD,
                "Nội dung trả lời hạn chế",
                "Người điều khiển xe máy vượt đèn đỏ có thể bị xử phạt.",
                "Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.",
                null,
                List.of("Điều 7 Nghị định 168 [Nguồn 1]"),
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

    private ChatAnswerResponse finalAnswer() {
        return new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                null,
                ResponseMode.STANDARD,
                "Nội dung trả lời cuối cùng",
                "Người điều khiển xe máy vượt đèn đỏ có thể bị xử phạt.",
                "Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.",
                null,
                List.of("Điều 7 Nghị định 168 [Nguồn 1]"),
                List.of("Phạt tiền [Nguồn 1]"),
                List.of(),
                List.of(),
                List.of("Đối chiếu biên bản"),
                List.of(),
                List.of(),
                new com.vn.traffic.chatbot.chat.api.dto.ScenarioAnalysisResponse(
                        List.of("Người điều khiển dùng xe máy", "Hành vi: vượt đèn đỏ"),
                        "Áp dụng Điều 7 Nghị định 168 [Nguồn 1]",
                        "Người điều khiển xe máy vượt đèn đỏ có thể bị xử phạt theo khung tương ứng [Nguồn 1]",
                        List.of("Giữ lại biên bản để đối chiếu"),
                        List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"))
                ),
                List.of(new CitationResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7", "Trích dẫn")),
                List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"))
        );
    }
}
