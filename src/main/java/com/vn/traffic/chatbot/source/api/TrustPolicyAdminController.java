package com.vn.traffic.chatbot.source.api;

import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.api.ResponseGeneral;
import com.vn.traffic.chatbot.source.api.dto.TrustPolicyRequest;
import com.vn.traffic.chatbot.source.api.dto.TrustPolicyResponse;
import com.vn.traffic.chatbot.source.service.SourceTrustPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for SourceTrustPolicy CRUD administration.
 *
 * <p>Endpoints:
 * GET    /api/v1/admin/trust-policies       — list all policies
 * POST   /api/v1/admin/trust-policies       — create a policy (201)
 * PUT    /api/v1/admin/trust-policies/{id}  — update a policy (200)
 * DELETE /api/v1/admin/trust-policies/{id}  — delete a policy (204)
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TrustPolicyAdminController {

    private final SourceTrustPolicyService trustPolicyService;

    @GetMapping(ApiPaths.TRUST_POLICIES)
    public ResponseEntity<ResponseGeneral<List<TrustPolicyResponse>>> list() {
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Trust policies", trustPolicyService.findAll()));
    }

    @PostMapping(ApiPaths.TRUST_POLICIES)
    public ResponseEntity<ResponseGeneral<TrustPolicyResponse>> create(
            @RequestBody @Valid TrustPolicyRequest request) {
        TrustPolicyResponse created = trustPolicyService.create(request);
        return ResponseEntity.status(201).body(ResponseGeneral.ofCreated("Trust policy created", created));
    }

    @PutMapping(ApiPaths.TRUST_POLICY_BY_ID)
    public ResponseEntity<ResponseGeneral<TrustPolicyResponse>> update(
            @PathVariable UUID policyId,
            @RequestBody @Valid TrustPolicyRequest request) {
        TrustPolicyResponse updated = trustPolicyService.update(policyId, request);
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Trust policy updated", updated));
    }

    @DeleteMapping(ApiPaths.TRUST_POLICY_BY_ID)
    public ResponseEntity<Void> delete(@PathVariable UUID policyId) {
        trustPolicyService.delete(policyId);
        return ResponseEntity.noContent().build();
    }
}
