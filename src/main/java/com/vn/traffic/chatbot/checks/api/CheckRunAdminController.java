package com.vn.traffic.chatbot.checks.api;

import com.vn.traffic.chatbot.checks.api.dto.CheckResultResponse;
import com.vn.traffic.chatbot.checks.api.dto.CheckRunDetailResponse;
import com.vn.traffic.chatbot.checks.api.dto.CheckRunResponse;
import com.vn.traffic.chatbot.checks.domain.CheckRun;
import com.vn.traffic.chatbot.checks.service.CheckRunService;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.api.ResponseGeneral;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CheckRunAdminController {

    private final CheckRunService checkRunService;

    @PostMapping(ApiPaths.CHECK_RUNS_TRIGGER)
    public ResponseEntity<ResponseGeneral<Map<String, UUID>>> trigger(
            @RequestParam(required = false) String chatModelId,
            @RequestParam(required = false) String evaluatorModelId) {
        CheckRun run = checkRunService.trigger(chatModelId, evaluatorModelId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ResponseGeneral.ofCreated("Check run triggered", Map.of("runId", run.getId())));
    }

    @GetMapping(ApiPaths.CHECK_RUNS)
    public ResponseGeneral<List<CheckRunResponse>> list() {
        return ResponseGeneral.ofSuccess("Check runs", checkRunService.findAll().stream().map(CheckRunResponse::fromEntity).toList());
    }

    @GetMapping(ApiPaths.CHECK_RUN_BY_ID)
    public ResponseEntity<ResponseGeneral<CheckRunDetailResponse>> getById(@PathVariable UUID runId) {
        return checkRunService.findById(runId)
                .map(r -> ResponseEntity.ok(ResponseGeneral.ofSuccess("Check run detail", CheckRunDetailResponse.fromEntity(r))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(ApiPaths.CHECK_RUN_RESULTS)
    public ResponseGeneral<List<CheckResultResponse>> results(@PathVariable UUID runId) {
        return ResponseGeneral.ofSuccess("Check run results", checkRunService.findResults(runId).stream()
                .map(CheckResultResponse::fromEntity)
                .toList());
    }
}
