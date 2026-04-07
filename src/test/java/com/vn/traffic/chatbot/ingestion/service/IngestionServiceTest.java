package com.vn.traffic.chatbot.ingestion.service;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.ingestion.api.dto.IngestionAcceptedResponse;
import com.vn.traffic.chatbot.ingestion.api.dto.UrlSourceRequest;
import com.vn.traffic.chatbot.ingestion.domain.IngestionJobStatus;
import com.vn.traffic.chatbot.ingestion.domain.JobType;
import com.vn.traffic.chatbot.ingestion.domain.KbIngestionJob;
import com.vn.traffic.chatbot.ingestion.orchestrator.IngestionOrchestrator;
import com.vn.traffic.chatbot.ingestion.parser.UrlPageParser;
import com.vn.traffic.chatbot.ingestion.repo.KbIngestionJobRepository;
import com.vn.traffic.chatbot.ingestion.repo.KbSourceFetchSnapshotRepository;
import com.vn.traffic.chatbot.source.domain.KbSource;
import com.vn.traffic.chatbot.source.domain.KbSourceVersion;
import com.vn.traffic.chatbot.source.repo.KbSourceRepository;
import com.vn.traffic.chatbot.source.repo.KbSourceVersionRepository;
import com.vn.traffic.chatbot.source.service.SourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private SourceService sourceService;

    @Mock
    private KbSourceRepository sourceRepo;

    @Mock
    private KbSourceVersionRepository versionRepo;

    @Mock
    private KbIngestionJobRepository jobRepo;

    @Mock
    private KbSourceFetchSnapshotRepository fetchSnapshotRepo;

    @Mock
    private IngestionOrchestrator orchestrator;

    @Mock
    private UrlPageParser urlPageParser;

    @InjectMocks
    private IngestionService ingestionService;

    // -----------------------------------------------------------------------
    // Test 1: submitUpload creates a job with status=QUEUED and jobType=FILE_UPLOAD
    // -----------------------------------------------------------------------
    @Test
    void submitUpload_createsJobWithQueuedStatusAndFileUploadType() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "dummy content".getBytes());

        KbSource mockSource = KbSource.builder().id(UUID.randomUUID()).build();
        KbSourceVersion mockVersion = KbSourceVersion.builder().id(UUID.randomUUID()).source(mockSource).build();
        when(sourceService.createSource(any())).thenReturn(mockSource);
        when(versionRepo.save(any())).thenReturn(mockVersion);

        ArgumentCaptor<KbIngestionJob> jobCaptor = ArgumentCaptor.forClass(KbIngestionJob.class);
        KbIngestionJob savedJob = KbIngestionJob.builder()
                .id(UUID.randomUUID()).source(mockSource)
                .status(IngestionJobStatus.QUEUED).jobType(JobType.FILE_UPLOAD).build();
        when(jobRepo.save(any())).thenReturn(savedJob);
        when(orchestrator.runPipeline(any())).thenReturn(null);

        ingestionService.submitUpload(file, "Traffic Rules", "MOT", "admin");

        verify(jobRepo).save(jobCaptor.capture());
        KbIngestionJob capturedJob = jobCaptor.getValue();
        assertThat(capturedJob.getStatus()).isEqualTo(IngestionJobStatus.QUEUED);
        assertThat(capturedJob.getJobType()).isEqualTo(JobType.FILE_UPLOAD);
    }

    // -----------------------------------------------------------------------
    // Test 2: submitUpload creates a KbSourceVersion with mimeType and fileName
    // -----------------------------------------------------------------------
    @Test
    void submitUpload_createsSourceVersionWithMimeTypeAndFileName() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "traffic-law.pdf", "application/pdf", "content".getBytes());

        KbSource mockSource = KbSource.builder().id(UUID.randomUUID()).build();
        KbSourceVersion mockVersion = KbSourceVersion.builder().id(UUID.randomUUID()).source(mockSource).build();
        when(sourceService.createSource(any())).thenReturn(mockSource);

        ArgumentCaptor<KbSourceVersion> versionCaptor = ArgumentCaptor.forClass(KbSourceVersion.class);
        when(versionRepo.save(versionCaptor.capture())).thenReturn(mockVersion);

        KbIngestionJob savedJob = KbIngestionJob.builder().id(UUID.randomUUID()).source(mockSource)
                .status(IngestionJobStatus.QUEUED).jobType(JobType.FILE_UPLOAD).build();
        when(jobRepo.save(any())).thenReturn(savedJob);
        when(orchestrator.runPipeline(any())).thenReturn(null);

        ingestionService.submitUpload(file, "Traffic Rules", "MOT", "admin");

        KbSourceVersion capturedVersion = versionCaptor.getValue();
        assertThat(capturedVersion.getMimeType()).isEqualTo("application/pdf");
        assertThat(capturedVersion.getFileName()).isEqualTo("traffic-law.pdf");
    }

    // -----------------------------------------------------------------------
    // Test 3: submitUpload returns IngestionAcceptedResponse with non-null sourceId and jobId
    // -----------------------------------------------------------------------
    @Test
    void submitUpload_returnsAcceptedResponseWithSourceIdAndJobId() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "data".getBytes());

        UUID sourceId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        KbSource mockSource = KbSource.builder().id(sourceId).build();
        KbSourceVersion mockVersion = KbSourceVersion.builder().id(UUID.randomUUID()).source(mockSource).build();
        KbIngestionJob savedJob = KbIngestionJob.builder().id(jobId).source(mockSource)
                .status(IngestionJobStatus.QUEUED).jobType(JobType.FILE_UPLOAD).build();

        when(sourceService.createSource(any())).thenReturn(mockSource);
        when(versionRepo.save(any())).thenReturn(mockVersion);
        when(jobRepo.save(any())).thenReturn(savedJob);
        when(orchestrator.runPipeline(any())).thenReturn(null);

        IngestionAcceptedResponse response = ingestionService.submitUpload(file, "Doc", null, "admin");

        assertThat(response.sourceId()).isNotNull();
        assertThat(response.jobId()).isNotNull();
        assertThat(response.status()).isEqualTo("QUEUED");
    }

    // -----------------------------------------------------------------------
    // Test 4: submitUrl creates a job with jobType=URL_IMPORT
    // -----------------------------------------------------------------------
    @Test
    void submitUrl_createsJobWithUrlImportType() throws Exception {
        UrlSourceRequest req = new UrlSourceRequest(
                "https://thuvienphapluat.vn/traffic", "Traffic Page", "TVPL", "admin");

        UUID sourceId = UUID.randomUUID();
        KbSource mockSource = KbSource.builder().id(sourceId).build();
        KbSourceVersion mockVersion = KbSourceVersion.builder().id(UUID.randomUUID()).source(mockSource).build();
        KbIngestionJob savedJob = KbIngestionJob.builder().id(UUID.randomUUID()).source(mockSource)
                .status(IngestionJobStatus.QUEUED).jobType(JobType.URL_IMPORT).build();

        doNothing().when(urlPageParser).validateHost(anyString());
        when(sourceService.createSource(any())).thenReturn(mockSource);
        when(versionRepo.save(any())).thenReturn(mockVersion);
        when(jobRepo.save(any())).thenReturn(savedJob);
        when(orchestrator.runPipeline(any())).thenReturn(null);

        ArgumentCaptor<KbIngestionJob> jobCaptor = ArgumentCaptor.forClass(KbIngestionJob.class);
        when(jobRepo.save(jobCaptor.capture())).thenReturn(savedJob);

        ingestionService.submitUrl(req);

        KbIngestionJob captured = jobCaptor.getValue();
        assertThat(captured.getJobType()).isEqualTo(JobType.URL_IMPORT);
    }

    // -----------------------------------------------------------------------
    // Test 5: submitUrl with private-IP URL propagates AppException(URL_NOT_ALLOWED)
    // -----------------------------------------------------------------------
    @Test
    void submitUrl_withPrivateIpUrl_throwsUrlNotAllowedBeforeJobCreation() {
        UrlSourceRequest req = new UrlSourceRequest(
                "http://192.168.1.1/admin", "Internal", null, "admin");

        doThrow(new AppException(ErrorCode.URL_NOT_ALLOWED, "Private address"))
                .when(urlPageParser).validateHost(anyString());

        assertThatThrownBy(() -> ingestionService.submitUrl(req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.URL_NOT_ALLOWED);

        verify(jobRepo, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Test 6: getJob with non-existent jobId throws AppException(JOB_NOT_FOUND)
    // -----------------------------------------------------------------------
    @Test
    void getJob_withNonExistentId_throwsJobNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(jobRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.getJob(unknownId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.JOB_NOT_FOUND);
    }

    // -----------------------------------------------------------------------
    // Test 7: listJobs returns paged results
    // -----------------------------------------------------------------------
    @Test
    void listJobs_returnsPagedResults() {
        var pageable = PageRequest.of(0, 10);
        KbSource mockSource = KbSource.builder().id(UUID.randomUUID()).build();
        KbIngestionJob job = KbIngestionJob.builder().id(UUID.randomUUID()).source(mockSource)
                .status(IngestionJobStatus.QUEUED).jobType(JobType.FILE_UPLOAD).build();
        when(jobRepo.findAll(pageable)).thenReturn(new PageImpl<>(List.of(job)));

        var result = ingestionService.listJobs(null, pageable);

        assertThat(result.getContent()).hasSize(1);
    }
}
