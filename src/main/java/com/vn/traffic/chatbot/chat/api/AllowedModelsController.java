package com.vn.traffic.chatbot.chat.api;

import com.vn.traffic.chatbot.common.config.AiModelProperties;
import com.vn.traffic.chatbot.chat.api.dto.AllowedModelResponse;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.api.ResponseGeneral;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes the YAML-driven model catalog as a REST endpoint.
 *
 * <p>{@code GET /api/v1/admin/allowed-models} returns the list of models defined in
 * {@code app.ai.models} so that the frontend can populate model selection dropdowns
 * without hardcoding any model IDs.
 */
@RestController
@RequiredArgsConstructor
public class AllowedModelsController {

    private final AiModelProperties modelProperties;

    @GetMapping(ApiPaths.ALLOWED_MODELS)
    public ResponseEntity<ResponseGeneral<List<AllowedModelResponse>>> list() {
        List<AllowedModelResponse> models = modelProperties.models().stream()
                .map(m -> new AllowedModelResponse(m.id(), m.displayName()))
                .toList();
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Allowed models", models));
    }
}
