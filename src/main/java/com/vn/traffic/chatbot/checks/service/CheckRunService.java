package com.vn.traffic.chatbot.checks.service;

import com.vn.traffic.chatbot.checks.domain.CheckResult;
import com.vn.traffic.chatbot.checks.domain.CheckRun;
import com.vn.traffic.chatbot.checks.domain.CheckRunStatus;
import com.vn.traffic.chatbot.checks.repo.CheckResultRepository;
import com.vn.traffic.chatbot.checks.repo.CheckRunRepository;
import com.vn.traffic.chatbot.parameter.domain.AiParameterSet;
import com.vn.traffic.chatbot.parameter.service.AiParameterSetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckRunService {

    private final CheckRunRepository checkRunRepository;
    private final CheckResultRepository checkResultRepository;
    private final AiParameterSetService aiParameterSetService;
    private final CheckRunner checkRunner;

    @Transactional
    public CheckRun trigger() {
        AiParameterSet activeParamSet = aiParameterSetService.getActive().orElse(null);
        CheckRun run = CheckRun.builder()
                .status(CheckRunStatus.RUNNING)
                .parameterSetId(activeParamSet != null ? activeParamSet.getId() : null)
                .parameterSetName(activeParamSet != null ? activeParamSet.getName() : "Unknown")
                .build();
        run = checkRunRepository.save(run);
        // Fire the async runner only after this transaction commits so the CheckRun row
        // is visible to the new thread. Calling checkRunner.runAll() directly here would
        // cause "CheckRun not found" because @Async spawns a new thread before commit.
        UUID runId = run.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                checkRunner.runAll(runId);
            }
        });
        return run;
    }

    @Transactional(readOnly = true)
    public List<CheckRun> findAll() {
        return checkRunRepository.findAllByOrderByCreatedDateDesc();
    }

    @Transactional(readOnly = true)
    public Optional<CheckRun> findById(UUID id) {
        return checkRunRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<CheckResult> findResults(UUID runId) {
        return checkResultRepository.findByCheckRunId(runId);
    }
}
