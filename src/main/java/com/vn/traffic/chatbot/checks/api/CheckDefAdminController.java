package com.vn.traffic.chatbot.checks.api;

import com.vn.traffic.chatbot.checks.api.dto.CheckDefRequest;
import com.vn.traffic.chatbot.checks.api.dto.CheckDefResponse;
import com.vn.traffic.chatbot.checks.service.CheckDefService;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.api.ResponseGeneral;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CheckDefAdminController {

    private final CheckDefService checkDefService;

    @GetMapping(ApiPaths.CHECK_DEFS)
    public ResponseGeneral<List<CheckDefResponse>> list() {
        return ResponseGeneral.ofSuccess("Check definitions", checkDefService.findAll().stream().map(CheckDefResponse::fromEntity).toList());
    }

    @PostMapping(ApiPaths.CHECK_DEFS)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseGeneral<CheckDefResponse> create(@Valid @RequestBody CheckDefRequest request) {
        return ResponseGeneral.ofCreated("Check definition created", CheckDefResponse.fromEntity(checkDefService.create(request)));
    }

    @PutMapping(ApiPaths.CHECK_DEF_BY_ID)
    public ResponseGeneral<CheckDefResponse> update(@PathVariable UUID defId, @Valid @RequestBody CheckDefRequest request) {
        return ResponseGeneral.ofSuccess("Check definition updated", CheckDefResponse.fromEntity(checkDefService.update(defId, request)));
    }

    @DeleteMapping(ApiPaths.CHECK_DEF_BY_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID defId) {
        checkDefService.delete(defId);
    }
}
