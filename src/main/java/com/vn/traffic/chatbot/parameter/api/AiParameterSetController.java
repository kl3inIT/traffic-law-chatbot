package com.vn.traffic.chatbot.parameter.api;

import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.parameter.api.dto.AiParameterSetResponse;
import com.vn.traffic.chatbot.parameter.api.dto.CreateAiParameterSetRequest;
import com.vn.traffic.chatbot.parameter.api.dto.UpdateAiParameterSetRequest;
import com.vn.traffic.chatbot.parameter.domain.AiParameterSet;
import com.vn.traffic.chatbot.parameter.service.AiParameterSetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.PARAMETER_SETS)
@RequiredArgsConstructor
@Slf4j
public class AiParameterSetController {

    private final AiParameterSetService service;

    @GetMapping
    public ResponseEntity<List<AiParameterSetResponse>> list() {
        List<AiParameterSetResponse> result = service.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{parameterSetId}")
    public ResponseEntity<AiParameterSetResponse> getById(@PathVariable UUID parameterSetId) {
        return ResponseEntity.ok(toResponse(service.findById(parameterSetId)));
    }

    @PostMapping
    public ResponseEntity<AiParameterSetResponse> create(@Valid @RequestBody CreateAiParameterSetRequest request) {
        log.info("Creating AI parameter set: {}", request.name());
        AiParameterSet created = service.create(request.name(), request.content(), request.chatModel(), request.evaluatorModel());
        return ResponseEntity.status(201).body(toResponse(created));
    }

    @PutMapping("/{parameterSetId}")
    public ResponseEntity<AiParameterSetResponse> update(
            @PathVariable UUID parameterSetId,
            @Valid @RequestBody UpdateAiParameterSetRequest request) {
        AiParameterSet updated = service.update(parameterSetId, request.name(), request.content(), request.chatModel(), request.evaluatorModel());
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{parameterSetId}")
    public ResponseEntity<Void> delete(@PathVariable UUID parameterSetId) {
        service.delete(parameterSetId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{parameterSetId}/activate")
    public ResponseEntity<AiParameterSetResponse> activate(@PathVariable UUID parameterSetId) {
        AiParameterSet activated = service.activate(parameterSetId);
        return ResponseEntity.ok(toResponse(activated));
    }

    @PostMapping("/{parameterSetId}/copy")
    public ResponseEntity<AiParameterSetResponse> copy(@PathVariable UUID parameterSetId) {
        log.info("Copying AI parameter set: {}", parameterSetId);
        AiParameterSet copied = service.copy(parameterSetId);
        return ResponseEntity.status(201).body(toResponse(copied));
    }

    private AiParameterSetResponse toResponse(AiParameterSet entity) {
        return new AiParameterSetResponse(
                entity.getId(),
                entity.getName(),
                entity.isActive(),
                entity.getContent(),
                entity.getChatModel(),
                entity.getEvaluatorModel(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

}
