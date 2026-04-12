package com.vn.traffic.chatbot.checks.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckRunner {

    private final CheckRunRepository checkRunRepository;
    private final CheckDefRepository checkDefRepository;
    private final CheckResultRepository checkResultRepository;
    private final ChatService chatService;
    private final SemanticEvaluator evaluator;

    @Async("ingestionExecutor")
    @Transactional
    public void runAll(UUID checkRunId) {
        CheckRun run = checkRunRepository.findById(checkRunId)
                .orElseThrow(() -> new IllegalStateException("CheckRun not found: " + checkRunId));
        List<CheckDef> activeDefs = checkDefRepository.findByActiveTrue();

        if (activeDefs.isEmpty()) {
            run.setAverageScore(0.0);
            run.setCheckCount(0);
            run.setStatus(CheckRunStatus.FAILED);
            checkRunRepository.save(run);
            return;
        }

        List<CheckResult> results = new ArrayList<>();
        for (CheckDef def : activeDefs) {
            results.add(runSingle(def, run));
        }

        double avg = results.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                .average()
                .orElse(0.0);

        checkResultRepository.saveAll(results);
        run.setAverageScore(avg);
        run.setCheckCount(results.size());
        run.setStatus(CheckRunStatus.COMPLETED);
        checkRunRepository.save(run);
    }

    private CheckResult runSingle(CheckDef def, CheckRun run) {
        CheckResult result = CheckResult.builder()
                .checkDef(def)
                .checkRun(run)
                .question(def.getQuestion())
                .referenceAnswer(def.getReferenceAnswer())
                .build();
        try {
            ChatAnswerResponse response = chatService.answer(def.getQuestion());
            String actualAnswer = response.answer();
            double score = evaluator.evaluate(def.getReferenceAnswer(), actualAnswer);
            result.setActualAnswer(actualAnswer);
            result.setScore(score);
        } catch (Exception ex) {
            log.error("CheckRunner: single def {} failed: {}", def.getId(), ex.getMessage(), ex);
            result.setActualAnswer(null);
            result.setScore(0.0);
            result.setLog("error: " + ex.getMessage());
        }
        return result;
    }
}
