package com.vn.traffic.chatbot.parameter.api;

import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.parameter.domain.AllowedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class AllowedModelController {

    @GetMapping(ApiPaths.ALLOWED_MODELS)
    public List<AllowedModelResponse> getAllowedModels() {
        return Arrays.stream(AllowedModel.values())
                .map(m -> new AllowedModelResponse(m.getModelId(), m.getDisplayName()))
                .toList();
    }

    public record AllowedModelResponse(String modelId, String displayName) {}
}
