package com.vn.traffic.chatbot.source.trust;

import com.vn.traffic.chatbot.source.api.TrustPolicyAdminController;
import com.vn.traffic.chatbot.source.api.dto.TrustPolicyRequest;
import com.vn.traffic.chatbot.source.api.dto.TrustPolicyResponse;
import com.vn.traffic.chatbot.source.domain.TrustTier;
import com.vn.traffic.chatbot.source.service.SourceTrustPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Wave 0 RED stubs — these tests will fail until T2 implements TrustPolicyAdminController.
 *
 * <p>Tests cover:
 * 1. POST /api/v1/admin/trust-policies creates a policy and returns 201.
 * 2. GET /api/v1/admin/trust-policies returns a list.
 */
@ExtendWith(MockitoExtension.class)
class TrustPolicyControllerTest {

    @Mock
    private SourceTrustPolicyService trustPolicyService;

    @InjectMocks
    private TrustPolicyAdminController controller;

    @Test
    void createTrustPolicy_returnsHttp201() {
        // Arrange
        var request = new TrustPolicyRequest(
                "Thư viện pháp luật",
                "thuvienphapluat.vn",
                "WEBSITE_PAGE",
                TrustTier.PRIMARY,
                "Official legal database"
        );
        var created = new TrustPolicyResponse(
                UUID.randomUUID(),
                "Thư viện pháp luật",
                "thuvienphapluat.vn",
                "WEBSITE_PAGE",
                TrustTier.PRIMARY,
                "Official legal database",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(trustPolicyService.create(any())).thenReturn(created);

        // Act
        ResponseEntity<TrustPolicyResponse> response = controller.create(request);

        // Assert
        assertThat(response.getStatusCodeValue()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().trustTier()).isEqualTo(TrustTier.PRIMARY);
    }

    @Test
    void listTrustPolicies_returnsHttp200WithList() {
        // Arrange
        var policy = new TrustPolicyResponse(
                UUID.randomUUID(), "Policy A", null, null,
                TrustTier.SECONDARY, null, OffsetDateTime.now(), OffsetDateTime.now()
        );
        when(trustPolicyService.findAll()).thenReturn(List.of(policy));

        // Act
        ResponseEntity<List<TrustPolicyResponse>> response = controller.list();

        // Assert
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
    }
}
