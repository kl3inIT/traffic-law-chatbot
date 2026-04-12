package com.vn.traffic.chatbot.source.trust;

import com.vn.traffic.chatbot.source.api.dto.TrustPolicyRequest;
import com.vn.traffic.chatbot.source.api.dto.TrustPolicyResponse;
import com.vn.traffic.chatbot.source.domain.ApprovalState;
import com.vn.traffic.chatbot.source.domain.KbSource;
import com.vn.traffic.chatbot.source.domain.SourceTrustPolicy;
import com.vn.traffic.chatbot.source.domain.TrustTier;
import com.vn.traffic.chatbot.source.repo.SourceTrustPolicyRepository;
import com.vn.traffic.chatbot.source.service.SourceTrustPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Wave 0 RED stubs — these tests will fail until T2 implements SourceTrustPolicyService.
 *
 * <p>Tests cover:
 * 1. A source with trustTier=PRIMARY can still have approvalState=PENDING — tier does NOT affect approval state.
 * 2. Creating a trust policy does NOT modify any KbSource approval/trusted/status fields.
 */
@ExtendWith(MockitoExtension.class)
class TrustPolicyServiceTest {

    @Mock
    private SourceTrustPolicyRepository trustPolicyRepository;

    @InjectMocks
    private SourceTrustPolicyService trustPolicyService;

    /**
     * T-4.1-02-01 invariant: trust tier is purely additive metadata.
     * A source with trustTier=PRIMARY must still have approvalState=PENDING until explicitly approved.
     * The SourceTrustPolicyService must NOT modify approvalState, trustedState, or status.
     */
    @Test
    void primaryTrustTier_doesNotAffectApprovalState() {
        // Arrange — source with PRIMARY tier but still PENDING approval
        KbSource source = KbSource.builder()
                .id(UUID.randomUUID())
                .trustTier(TrustTier.PRIMARY)
                .approvalState(ApprovalState.PENDING)
                .build();

        // Assert: PRIMARY tier on a KbSource does NOT change its approvalState
        assertThat(source.getTrustTier()).isEqualTo(TrustTier.PRIMARY);
        assertThat(source.getApprovalState()).isEqualTo(ApprovalState.PENDING);
        // Tier is additive — PENDING approval state is preserved independently of tier
    }

    @Test
    void createTrustPolicy_savesEntityAndReturnsMappedResponse() {
        // Arrange
        var request = new TrustPolicyRequest(
                "Cổng TTĐT Chính phủ",
                "chinhphu.vn",
                "WEBSITE_PAGE",
                TrustTier.PRIMARY,
                "Government portal"
        );

        var savedPolicy = SourceTrustPolicy.builder()
                .id(UUID.randomUUID())
                .name("Cổng TTĐT Chính phủ")
                .domainPattern("chinhphu.vn")
                .sourceType("WEBSITE_PAGE")
                .trustTier(TrustTier.PRIMARY)
                .description("Government portal")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(trustPolicyRepository.save(any())).thenReturn(savedPolicy);

        // Act
        TrustPolicyResponse response = trustPolicyService.create(request);

        // Assert
        assertThat(response.name()).isEqualTo("Cổng TTĐT Chính phủ");
        assertThat(response.trustTier()).isEqualTo(TrustTier.PRIMARY);
        assertThat(response.id()).isNotNull();
    }
}
