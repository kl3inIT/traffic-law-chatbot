package com.vn.traffic.chatbot.source.service;

import com.vn.traffic.chatbot.chunk.service.ChunkMetadataUpdater;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.source.api.dto.ApprovalRequest;
import com.vn.traffic.chatbot.source.api.dto.CreateSourceRequest;
import com.vn.traffic.chatbot.source.domain.*;
import com.vn.traffic.chatbot.source.repo.KbSourceApprovalEventRepository;
import com.vn.traffic.chatbot.source.repo.KbSourceRepository;
import com.vn.traffic.chatbot.source.repo.KbSourceVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private KbSourceRepository sourceRepo;

    @Mock
    private KbSourceVersionRepository versionRepo;

    @Mock
    private KbSourceApprovalEventRepository approvalEventRepo;

    @Mock
    private ChunkMetadataUpdater chunkMetadataUpdater;

    @InjectMocks
    private SourceService sourceService;

    // Test 1: createSource returns source with DRAFT/PENDING/UNTRUSTED defaults
    @Test
    void createSource_givenValidRequest_returnsSavedSourceWithCorrectDefaults() {
        var request = new CreateSourceRequest(
                SourceType.PDF,
                "Luat Giao Thong",
                OriginKind.FILE_UPLOAD,
                "uploads/doc.pdf",
                "Bo Cong An",
                "vi",
                "admin"
        );

        var savedSource = KbSource.builder()
                .id(UUID.randomUUID())
                .sourceType(SourceType.PDF)
                .title("Luat Giao Thong")
                .originKind(OriginKind.FILE_UPLOAD)
                .status(SourceStatus.DRAFT)
                .approvalState(ApprovalState.PENDING)
                .trustedState(TrustedState.UNTRUSTED)
                .build();

        when(sourceRepo.save(any(KbSource.class))).thenReturn(savedSource);

        var result = sourceService.createSource(request);

        assertThat(result.getStatus()).isEqualTo(SourceStatus.DRAFT);
        assertThat(result.getApprovalState()).isEqualTo(ApprovalState.PENDING);
        assertThat(result.getTrustedState()).isEqualTo(TrustedState.UNTRUSTED);
    }

    // Test 2: createSource captures provenance fields from request
    @Test
    void createSource_capturesProvenanceFromRequest() {
        var request = new CreateSourceRequest(
                SourceType.WORD,
                "Nghi Dinh 100",
                OriginKind.URL_IMPORT,
                "https://example.gov.vn/nd100.docx",
                "Chinh Phu",
                "vi",
                "importer"
        );

        ArgumentCaptor<KbSource> captor = ArgumentCaptor.forClass(KbSource.class);
        when(sourceRepo.save(captor.capture())).thenAnswer(inv -> captor.getValue());

        sourceService.createSource(request);

        var saved = captor.getValue();
        assertThat(saved.getSourceType()).isEqualTo(SourceType.WORD);
        assertThat(saved.getTitle()).isEqualTo("Nghi Dinh 100");
        assertThat(saved.getOriginKind()).isEqualTo(OriginKind.URL_IMPORT);
        assertThat(saved.getOriginValue()).isEqualTo("https://example.gov.vn/nd100.docx");
        assertThat(saved.getCreatedBy()).isEqualTo("importer");
    }

    // Test 3: approve() given PENDING source sets APPROVED and saves approval event
    @Test
    void approve_givenPendingSource_setsApprovedAndSavesApprovalEvent() {
        var sourceId = UUID.randomUUID();
        var source = buildPendingSource(sourceId);
        var approvalReq = new ApprovalRequest("Looks good", "reviewer");

        when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));
        when(sourceRepo.save(any())).thenReturn(source);

        ArgumentCaptor<KbSourceApprovalEvent> eventCaptor = ArgumentCaptor.forClass(KbSourceApprovalEvent.class);
        when(approvalEventRepo.save(eventCaptor.capture())).thenAnswer(inv -> eventCaptor.getValue());

        sourceService.approve(sourceId, approvalReq);

        assertThat(source.getApprovalState()).isEqualTo(ApprovalState.APPROVED);
        var event = eventCaptor.getValue();
        assertThat(event.getAction()).isEqualTo("APPROVE");
        assertThat(event.getPreviousState()).isEqualTo("PENDING");
        assertThat(event.getNewState()).isEqualTo("APPROVED");
    }

    // Test 4: approve() given non-PENDING source throws AppException(VALIDATION_ERROR)
    @Test
    void approve_givenAlreadyApprovedSource_throwsValidationError() {
        var sourceId = UUID.randomUUID();
        var source = KbSource.builder()
                .id(sourceId)
                .approvalState(ApprovalState.APPROVED)
                .status(SourceStatus.DRAFT)
                .trustedState(TrustedState.UNTRUSTED)
                .build();

        when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));

        var req = new ApprovalRequest("reason", "actor");
        assertThatThrownBy(() -> sourceService.approve(sourceId, req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // Test 5: reject() given PENDING source sets REJECTED and saves approval event
    @Test
    void reject_givenPendingSource_setsRejectedAndSavesApprovalEvent() {
        var sourceId = UUID.randomUUID();
        var source = buildPendingSource(sourceId);
        var req = new ApprovalRequest("Needs work", "reviewer");

        when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));
        when(sourceRepo.save(any())).thenReturn(source);

        ArgumentCaptor<KbSourceApprovalEvent> eventCaptor = ArgumentCaptor.forClass(KbSourceApprovalEvent.class);
        when(approvalEventRepo.save(eventCaptor.capture())).thenAnswer(inv -> eventCaptor.getValue());

        sourceService.reject(sourceId, req);

        assertThat(source.getApprovalState()).isEqualTo(ApprovalState.REJECTED);
        assertThat(eventCaptor.getValue().getAction()).isEqualTo("REJECT");
    }

    // Test 6: activate() given APPROVED source sets ACTIVE and TRUSTED
    @Test
    void activate_givenApprovedSource_setsActiveAndTrusted() {
        var sourceId = UUID.randomUUID();
        var source = KbSource.builder()
                .id(sourceId)
                .approvalState(ApprovalState.APPROVED)
                .status(SourceStatus.DRAFT)
                .trustedState(TrustedState.UNTRUSTED)
                .build();

        when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));
        when(sourceRepo.save(any())).thenReturn(source);
        when(approvalEventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sourceService.activate(sourceId, "admin");

        assertThat(source.getStatus()).isEqualTo(SourceStatus.ACTIVE);
        assertThat(source.getTrustedState()).isEqualTo(TrustedState.TRUSTED);
        verify(chunkMetadataUpdater).updateChunkMetadata(sourceId.toString(), true, true);
    }

    // Test 7: activate() given PENDING source throws AppException(VALIDATION_ERROR)
    @Test
    void activate_givenPendingSource_throwsValidationError() {
        var sourceId = UUID.randomUUID();
        var source = buildPendingSource(sourceId);

        when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> sourceService.activate(sourceId, "admin"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // Test 8: deactivate() given active source sets DISABLED
    @Test
    void deactivate_givenActiveSource_setsDisabled() {
        var sourceId = UUID.randomUUID();
        var source = KbSource.builder()
                .id(sourceId)
                .approvalState(ApprovalState.APPROVED)
                .status(SourceStatus.ACTIVE)
                .trustedState(TrustedState.TRUSTED)
                .build();

        when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));
        when(sourceRepo.save(any())).thenReturn(source);
        when(approvalEventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sourceService.deactivate(sourceId, "admin");

        assertThat(source.getStatus()).isEqualTo(SourceStatus.DISABLED);
        assertThat(source.getTrustedState()).isEqualTo(TrustedState.REVOKED);
        verify(chunkMetadataUpdater).updateChunkMetadata(sourceId.toString(), false, false);
    }

    // Test 9: getById() given non-existent UUID throws AppException(SOURCE_NOT_FOUND)
    @Test
    void getById_givenNonExistentId_throwsSourceNotFound() {
        var unknownId = UUID.randomUUID();
        when(sourceRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sourceService.getById(unknownId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SOURCE_NOT_FOUND));
    }

    // Helper
    private KbSource buildPendingSource(UUID id) {
        return KbSource.builder()
                .id(id)
                .sourceType(SourceType.PDF)
                .title("Test Source")
                .originKind(OriginKind.FILE_UPLOAD)
                .status(SourceStatus.DRAFT)
                .approvalState(ApprovalState.PENDING)
                .trustedState(TrustedState.UNTRUSTED)
                .build();
    }
}
