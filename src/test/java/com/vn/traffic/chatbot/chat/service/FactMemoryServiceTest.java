package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.domain.ChatMessage;
import com.vn.traffic.chatbot.chat.domain.ChatMessageRole;
import com.vn.traffic.chatbot.chat.domain.ChatMessageType;
import com.vn.traffic.chatbot.chat.domain.ChatThread;
import com.vn.traffic.chatbot.chat.domain.ThreadFact;
import com.vn.traffic.chatbot.chat.domain.ThreadFactStatus;
import com.vn.traffic.chatbot.chat.repo.ThreadFactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactMemoryServiceTest {

    @Mock
    private ThreadFactRepository threadFactRepository;

    private FactMemoryService factMemoryService;

    @BeforeEach
    void setUp() {
        factMemoryService = new FactMemoryService(threadFactRepository);
    }

    @Test
    void persistsOnlyExplicitUserFactsForThreadMemory() {
        ChatThread thread = ChatThread.builder().id(UUID.randomUUID()).build();
        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .thread(thread)
                .role(ChatMessageRole.USER)
                .messageType(ChatMessageType.QUESTION)
                .content("Tôi đi xe máy và vượt đèn đỏ, chatbot đừng tự đoán thêm.")
                .build();

        when(threadFactRepository.findFirstByThreadIdAndFactKeyAndStatusOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(threadFactRepository.save(any(ThreadFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ThreadFact> facts = factMemoryService.rememberExplicitFacts(thread, userMessage);

        assertThat(facts).extracting(ThreadFact::getFactKey)
                .containsExactly("vehicleType", "violationType");
        assertThat(facts).extracting(ThreadFact::getFactValue)
                .containsExactly("xe máy", "vượt đèn đỏ");
        verify(threadFactRepository, times(2)).save(any(ThreadFact.class));
    }

    @Test
    void correctionSupersedesPreviousActiveFactAndKeepsNewestActive() {
        ChatThread thread = ChatThread.builder().id(UUID.randomUUID()).build();
        ChatMessage original = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi đi xe máy.").build();
        ChatMessage corrected = ChatMessage.builder().id(UUID.randomUUID()).thread(thread).role(ChatMessageRole.USER).messageType(ChatMessageType.QUESTION).content("Tôi đính chính là đi ô tô.").build();
        ThreadFact existing = ThreadFact.builder()
                .id(UUID.randomUUID())
                .thread(thread)
                .sourceMessage(original)
                .factKey("vehicleType")
                .factValue("xe máy")
                .status(ThreadFactStatus.ACTIVE)
                .build();

        when(threadFactRepository.findFirstByThreadIdAndFactKeyAndStatusOrderByCreatedAtDesc(thread.getId(), "vehicleType", ThreadFactStatus.ACTIVE))
                .thenReturn(Optional.of(existing));
        when(threadFactRepository.save(any(ThreadFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ThreadFact> facts = factMemoryService.rememberExplicitFacts(thread, corrected);

        assertThat(facts).hasSize(1);
        ThreadFact newFact = facts.getFirst();
        assertThat(newFact.getFactKey()).isEqualTo("vehicleType");
        assertThat(newFact.getFactValue()).isEqualTo("ô tô");
        assertThat(newFact.getStatus()).isEqualTo(ThreadFactStatus.ACTIVE);
        assertThat(existing.getStatus()).isEqualTo(ThreadFactStatus.SUPERSEDED);
        assertThat(existing.getSupersededByFact()).isSameAs(newFact);
    }

    @Test
    void negatedVehicleTypeIsSkippedSoCorrectValueWins() {
        // "Thực ra tôi đi ô tô, không phải xe máy" — last regex match is "xe máy" (negated),
        // so only "ô tô" should be stored.
        List<FactMemoryService.ExtractedFact> facts = factMemoryService.extractExplicitFacts(
                "Thực ra tôi đi ô tô, không phải xe máy.");
        assertThat(facts).extracting(FactMemoryService.ExtractedFact::factKey).containsExactly("vehicleType");
        assertThat(facts).extracting(FactMemoryService.ExtractedFact::factValue).containsExactly("ô tô");
    }

    @Test
    void buildThreadAwareQuestionUsesActiveFactsInsteadOfReparsingHistory() {
        String enriched = factMemoryService.buildThreadAwareQuestion(
                "Nếu gây tai nạn thì sao?",
                List.of(
                        ThreadFact.builder().factKey("vehicleType").factValue("xe máy").status(ThreadFactStatus.ACTIVE).build(),
                        ThreadFact.builder().factKey("violationType").factValue("vượt đèn đỏ").status(ThreadFactStatus.ACTIVE).build()
                )
        );

        assertThat(enriched).contains("Nếu gây tai nạn thì sao?");
        assertThat(enriched).contains("vehicleType: xe máy");
        assertThat(enriched).contains("violationType: vượt đèn đỏ");
    }
}
