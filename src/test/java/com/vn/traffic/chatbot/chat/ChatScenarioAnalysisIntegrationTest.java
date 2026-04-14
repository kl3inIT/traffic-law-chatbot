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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatScenarioAnalysisIntegrationTest {

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
    void multiTurnThreadProducesFinalAnalysisWithSources() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = ChatThread.builder().id(threadId).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        ChatMessage firstUser = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi vượt đèn đỏ thì bị phạt sao?").build();
        ChatMessage firstAssistant = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.ASSISTANT).messageType(ChatMessageType.ANSWER).content("Bạn đi loại phương tiện nào?").build();
        ChatMessage secondUser = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi đi xe máy.").build();

        when(chatThreadRepository.save(any(ChatThread.class))).thenReturn(thread);
        when(chatThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(firstUser, firstAssistant, secondUser, secondUser);
        when(chatService.answer(anyString(), isNull(), anyString()))
                .thenReturn(initialAnswer())
                .thenReturn(finalAnswer());

        ChatAnswerResponse first = chatThreadService.createThread("Tôi vượt đèn đỏ thì bị phạt sao?");
        ChatAnswerResponse second = chatThreadService.postMessage(threadId, "Tôi đi xe máy.");

        assertThat(first.threadId()).isEqualTo(threadId);
        assertThat(second.responseMode()).isEqualTo(ResponseMode.FINAL_ANALYSIS);
        assertThat(second.threadId()).isEqualTo(threadId);
        assertThat(second.scenarioAnalysis()).isNotNull();
        assertThat(second.scenarioAnalysis().facts()).contains("Người điều khiển dùng xe máy", "Hành vi: vượt đèn đỏ");
        assertThat(second.scenarioAnalysis().rule()).contains("Điều 7");
        assertThat(second.scenarioAnalysis().outcome()).contains("xử phạt");
        assertThat(second.scenarioAnalysis().actions()).contains("Đối chiếu biên bản");
        assertThat(second.citations()).isNotEmpty();
        assertThat(second.sources()).isNotEmpty();
    }

    @Test
    void limitedGroundingStillProducesFinalAnalysis() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = ChatThread.builder().id(threadId).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        ChatMessage userMsg = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi vượt đèn đỏ bằng xe máy").build();

        when(chatThreadRepository.save(any(ChatThread.class))).thenReturn(thread);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(userMsg);
        when(chatService.answer(anyString(), isNull(), anyString())).thenReturn(limitedGroundingAnswer());

        ChatAnswerResponse response = chatThreadService.createThread("Tôi vượt đèn đỏ bằng xe máy");

        assertThat(response.responseMode()).isEqualTo(ResponseMode.FINAL_ANALYSIS);
        assertThat(response.scenarioAnalysis()).isNotNull();
        assertThat(response.scenarioAnalysis().facts()).isNotEmpty();
    }

    private ChatAnswerResponse initialAnswer() {
        return new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                null,
                ResponseMode.STANDARD,
                "Bạn điều khiển loại phương tiện nào?",
                null,
                "Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of()
        );
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
                List.of("Xe máy", "Vượt đèn đỏ"),
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
                List.of("Người điều khiển dùng xe máy", "Hành vi: vượt đèn đỏ"),
                new com.vn.traffic.chatbot.chat.api.dto.ScenarioAnalysisResponse(
                        List.of("Người điều khiển dùng xe máy", "Hành vi: vượt đèn đỏ"),
                        "Áp dụng Điều 7 Nghị định 168 [Nguồn 1]",
                        "Người điều khiển xe máy vượt đèn đỏ có thể bị xử phạt theo khung tương ứng [Nguồn 1]",
                        List.of("Giữ lại biên bản để đối chiếu", "Đối chiếu biên bản"),
                        List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"))
                ),
                List.of(new CitationResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7", "Trích dẫn")),
                List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"))
        );
    }
}
