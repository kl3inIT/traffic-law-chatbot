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
    public void runAll(UUID checkRunId) {
        try {
            CheckRun run = loadRun(checkRunId);
            List<CheckDef> activeDefs = loadActiveDefs();

            if (activeDefs.isEmpty()) {
                persistRunStatus(checkRunId, 0.0, 0, CheckRunStatus.FAILED);
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

            persistResults(results, run, avg);
        } catch (Exception ex) {
            log.error("CheckRunner: run {} failed unexpectedly: {}", checkRunId, ex.getMessage(), ex);
            checkRunRepository.findById(checkRunId).ifPresent(run -> {
                run.setStatus(CheckRunStatus.FAILED);
                checkRunRepository.save(run);
            });
        }
    }

    @Transactional(readOnly = true)
    protected CheckRun loadRun(UUID id) {
        return checkRunRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("CheckRun not found: " + id));
    }

    @Transactional(readOnly = true)
    protected List<CheckDef> loadActiveDefs() {
        return checkDefRepository.findByActiveTrue();
    }

    @Transactional
    protected void persistRunStatus(UUID runId, double avg, int count, CheckRunStatus status) {
        checkRunRepository.findById(runId).ifPresent(run -> {
            run.setAverageScore(avg);
            run.setCheckCount(count);
            run.setStatus(status);
            checkRunRepository.save(run);
        });
    }

    @Transactional
    protected void persistResults(List<CheckResult> results, CheckRun run, double avg) {
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
