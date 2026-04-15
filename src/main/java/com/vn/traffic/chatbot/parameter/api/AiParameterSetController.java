package com.vn.traffic.chatbot.parameter.api;

import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.api.ResponseGeneral;
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
    public ResponseEntity<ResponseGeneral<List<AiParameterSetResponse>>> list() {
        List<AiParameterSetResponse> result = service.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Parameter sets", result));
    }

    @GetMapping("/{parameterSetId}")
    public ResponseEntity<ResponseGeneral<AiParameterSetResponse>> getById(@PathVariable UUID parameterSetId) {
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Parameter set detail", toResponse(service.findById(parameterSetId))));
    }

    @PostMapping
    public ResponseEntity<ResponseGeneral<AiParameterSetResponse>> create(@Valid @RequestBody CreateAiParameterSetRequest request) {
        log.info("Creating AI parameter set: {}", request.name());
        AiParameterSet created = service.create(request.name(), request.content());
        return ResponseEntity.status(201).body(ResponseGeneral.ofCreated("Parameter set created", toResponse(created)));
    }

    @PutMapping("/{parameterSetId}")
    public ResponseEntity<ResponseGeneral<AiParameterSetResponse>> update(
            @PathVariable UUID parameterSetId,
            @Valid @RequestBody UpdateAiParameterSetRequest request) {
        AiParameterSet updated = service.update(parameterSetId, request.name(), request.content());
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Parameter set updated", toResponse(updated)));
    }

    @DeleteMapping("/{parameterSetId}")
    public ResponseEntity<Void> delete(@PathVariable UUID parameterSetId) {
        service.delete(parameterSetId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{parameterSetId}/activate")
    public ResponseEntity<ResponseGeneral<AiParameterSetResponse>> activate(@PathVariable UUID parameterSetId) {
        AiParameterSet activated = service.activate(parameterSetId);
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Parameter set activated", toResponse(activated)));
    }

    @PostMapping("/{parameterSetId}/copy")
    public ResponseEntity<ResponseGeneral<AiParameterSetResponse>> copy(@PathVariable UUID parameterSetId) {
        log.info("Copying AI parameter set: {}", parameterSetId);
        AiParameterSet copied = service.copy(parameterSetId);
        return ResponseEntity.status(201).body(ResponseGeneral.ofCreated("Parameter set copied", toResponse(copied)));
    }

    private AiParameterSetResponse toResponse(AiParameterSet entity) {
        return new AiParameterSetResponse(
                entity.getId(),
                entity.getName(),
                entity.isActive(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

}
