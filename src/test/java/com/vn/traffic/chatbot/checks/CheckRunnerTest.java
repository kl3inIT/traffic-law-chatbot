package com.vn.traffic.chatbot.checks;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.checks.domain.CheckDef;
import com.vn.traffic.chatbot.checks.domain.CheckResult;
import com.vn.traffic.chatbot.checks.domain.CheckRun;
import com.vn.traffic.chatbot.checks.domain.CheckRunStatus;
import com.vn.traffic.chatbot.checks.evaluator.SemanticEvaluator;
import com.vn.traffic.chatbot.checks.repo.CheckDefRepository;
import com.vn.traffic.chatbot.checks.repo.CheckResultRepository;
import com.vn.traffic.chatbot.checks.repo.CheckRunRepository;
import com.vn.traffic.chatbot.checks.service.CheckRunner;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckRunnerTest {

    @Mock
    private CheckRunRepository checkRunRepository;

    @Mock
    private CheckDefRepository checkDefRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private SemanticEvaluator evaluator;

    private CheckRunner checkRunner;

    @BeforeEach
    void setUp() {
        checkRunner = new CheckRunner(checkRunRepository, checkDefRepository, checkResultRepository, chatService, evaluator);
    }

    @Test
    void testRunAllSavesResultsAndSetsCompleted() {
        UUID runId = UUID.randomUUID();
        CheckRun run = CheckRun.builder()
                .id(runId)
                .status(CheckRunStatus.RUNNING)
                .build();

        CheckDef def1 = CheckDef.builder()
                .id(UUID.randomUUID())
                .question("Câu hỏi thứ nhất về luật giao thông?")
                .referenceAnswer("Câu trả lời tham chiếu thứ nhất về luật giao thông.")
                .active(true)
                .build();
        CheckDef def2 = CheckDef.builder()
                .id(UUID.randomUUID())
                .question("Câu hỏi thứ hai về quy tắc đường bộ?")
                .referenceAnswer("Câu trả lời tham chiếu thứ hai về quy tắc đường bộ.")
                .active(true)
                .build();

        ChatAnswerResponse chatResponse = new ChatAnswerResponse(
                null, null, null, "test answer", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null
        );

        when(checkRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(checkDefRepository.findByActiveTrue()).thenReturn(List.of(def1, def2));
        when(chatService.answer(anyString())).thenReturn(chatResponse);
        when(evaluator.evaluate(anyString(), anyString())).thenReturn(0.75);
        when(checkResultRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(checkRunRepository.save(any(CheckRun.class))).thenReturn(run);

        checkRunner.runAll(runId);

        ArgumentCaptor<List<CheckResult>> resultsCaptor = ArgumentCaptor.forClass(List.class);
        verify(checkResultRepository).saveAll(resultsCaptor.capture());
        assertThat(resultsCaptor.getValue()).hasSize(2);

        assertThat(run.getStatus()).isEqualTo(CheckRunStatus.COMPLETED);
        assertThat(run.getAverageScore()).isEqualTo(0.75);
    }

    @Test
    void testRunAllWithNoActiveDefsSetsFailed() {
        UUID runId = UUID.randomUUID();
        CheckRun run = CheckRun.builder()
                .id(runId)
                .status(CheckRunStatus.RUNNING)
                .build();

        when(checkRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(checkDefRepository.findByActiveTrue()).thenReturn(List.of());
        when(checkRunRepository.save(any(CheckRun.class))).thenReturn(run);

        checkRunner.runAll(runId);

        assertThat(run.getStatus()).isEqualTo(CheckRunStatus.FAILED);
        verify(checkResultRepository, never()).saveAll(anyList());
    }
}
