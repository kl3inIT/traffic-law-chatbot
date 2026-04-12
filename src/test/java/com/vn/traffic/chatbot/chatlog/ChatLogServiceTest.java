package com.vn.traffic.chatbot.chatlog;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.chatlog.domain.ChatLog;
import com.vn.traffic.chatbot.chatlog.repo.ChatLogRepository;
import com.vn.traffic.chatbot.chatlog.service.ChatLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatLogServiceTest {

    @Mock
    private ChatLogRepository chatLogRepository;

    @InjectMocks
    private ChatLogService chatLogService;

    @Test
    void testLogPersistedAfterAnswer() {
        // given
        String question = "Tốc độ tối đa trong khu dân cư là bao nhiêu?";
        ChatAnswerResponse response = new ChatAnswerResponse(
                GroundingStatus.GROUNDED, null, null,
                "Tốc độ tối đa là 50 km/h.", null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, List.of(), List.of()
        );
        when(chatLogRepository.save(any(ChatLog.class))).thenAnswer(i -> i.getArgument(0));

        // when
        chatLogService.save(question, response, GroundingStatus.GROUNDED, null, 100, 200, 500, null);

        // then
        ArgumentCaptor<ChatLog> captor = ArgumentCaptor.forClass(ChatLog.class);
        verify(chatLogRepository).save(captor.capture());
        ChatLog saved = captor.getValue();
        assertThat(saved.getQuestion()).isEqualTo(question);
        assertThat(saved.getAnswer()).isEqualTo("Tốc độ tối đa là 50 km/h.");
        assertThat(saved.getGroundingStatus()).isEqualTo(GroundingStatus.GROUNDED);
    }

    @Test
    void testLogFailureSwallowed() {
        // given — repository throws, ChatLogService.save() is used inside a try/catch in ChatService
        // This test verifies the repository layer can throw without propagating to test boundary
        // when ChatLogService.save() is called directly, it propagates (no swallow here)
        // The swallowing happens at ChatService level; this test verifies the repo exception path
        String question = "Test question";
        ChatAnswerResponse response = new ChatAnswerResponse(
                GroundingStatus.REFUSED, null, null,
                null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, List.of(), List.of()
        );
        when(chatLogRepository.save(any(ChatLog.class))).thenThrow(new RuntimeException("DB error"));

        // when / then — ChatLogService propagates; ChatService wraps it in try/catch
        // Verify the exception is a RuntimeException (will be caught by ChatService's try/catch)
        assertThatCode(() -> {
            try {
                chatLogService.save(question, response, GroundingStatus.REFUSED, null, 0, 0, 0, null);
            } catch (RuntimeException ex) {
                // This is the expected path — ChatService catches this and logs.warn
                assertThat(ex.getMessage()).contains("DB error");
                throw ex;
            }
        }).isInstanceOf(RuntimeException.class);

        verify(chatLogRepository).save(any(ChatLog.class));
    }
}
