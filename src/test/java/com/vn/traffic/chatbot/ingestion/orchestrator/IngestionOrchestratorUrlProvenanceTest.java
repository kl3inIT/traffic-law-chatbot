package com.vn.traffic.chatbot.ingestion.orchestrator;

import com.vn.traffic.chatbot.ingestion.chunking.TokenChunkingService;
import com.vn.traffic.chatbot.ingestion.domain.IngestionJobStatus;
import com.vn.traffic.chatbot.ingestion.domain.IngestionStep;
import com.vn.traffic.chatbot.ingestion.domain.JobType;
import com.vn.traffic.chatbot.ingestion.domain.KbIngestionJob;
import com.vn.traffic.chatbot.ingestion.domain.KbSourceFetchSnapshot;
import com.vn.traffic.chatbot.ingestion.fetch.FetchResult;
import com.vn.traffic.chatbot.ingestion.fetch.SafeUrlFetcher;
import com.vn.traffic.chatbot.ingestion.parser.ParsedDocument;
import com.vn.traffic.chatbot.ingestion.parser.TikaDocumentParser;
import com.vn.traffic.chatbot.ingestion.parser.UrlPageParser;
import com.vn.traffic.chatbot.ingestion.repo.KbIngestionJobRepository;
import com.vn.traffic.chatbot.ingestion.repo.KbSourceFetchSnapshotRepository;
import com.vn.traffic.chatbot.source.domain.KbSource;
import com.vn.traffic.chatbot.source.domain.KbSourceVersion;
import com.vn.traffic.chatbot.source.domain.SourceType;
import com.vn.traffic.chatbot.source.repo.KbSourceVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionOrchestratorUrlProvenanceTest {

    @Mock
    private KbIngestionJobRepository jobRepo;

    @Mock
    private KbSourceVersionRepository versionRepo;

    @Mock
    private KbSourceFetchSnapshotRepository fetchSnapshotRepo;

    @Mock
    private TikaDocumentParser tikaDocumentParser;

    @Mock
    private UrlPageParser urlPageParser;

    @Mock
    private SafeUrlFetcher safeUrlFetcher;

    @Mock
    private TokenChunkingService tokenChunkingService;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private IngestionOrchestrator orchestrator;

    @Test
    void runPipeline_persistsRequestedAndFinalUrlFromFetchContract() {
        UUID jobId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        String requestedUrl = "https://laws.example.vn/start";
        String finalUrl = "https://content.example.vn/final";

        KbSource source = KbSource.builder()
                .id(sourceId)
                .sourceType(SourceType.WEBSITE_PAGE)
                .title("Traffic page")
                .build();
        KbSourceVersion version = KbSourceVersion.builder()
                .id(versionId)
                .source(source)
                .canonicalUrl(requestedUrl)
                .processingVersion("1.0")
                .build();
        KbIngestionJob job = KbIngestionJob.builder()
                .id(jobId)
                .source(source)
                .sourceVersionId(versionId)
                .jobType(JobType.URL_IMPORT)
                .status(IngestionJobStatus.QUEUED)
                .build();
        FetchResult fetchResult = new FetchResult(
                requestedUrl,
                finalUrl,
                200,
                "text/html",
                "Traffic page",
                "<html><body>Noi dung</body></html>",
                "etag-1",
                null
        );
        ParsedDocument parsedDocument = new ParsedDocument(
                "Noi dung",
                "Traffic page",
                "text/html",
                "jsoup",
                "1.19.1",
                List.of(new ParsedDocument.PageSection(1, "full", "Noi dung"))
        );

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(versionRepo.findById(versionId)).thenReturn(Optional.of(version));
        when(safeUrlFetcher.fetch(requestedUrl)).thenReturn(fetchResult);
        when(urlPageParser.parseFetchedPage(fetchResult)).thenReturn(parsedDocument);
        when(tokenChunkingService.chunk(parsedDocument, sourceId.toString(), versionId.toString(), "1.0"))
                .thenReturn(List.of());
        when(versionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        orchestrator.runPipeline(jobId).join();

        ArgumentCaptor<KbSourceFetchSnapshot> snapshotCaptor = ArgumentCaptor.forClass(KbSourceFetchSnapshot.class);
        verify(fetchSnapshotRepo).save(snapshotCaptor.capture());
        KbSourceFetchSnapshot snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.getRequestedUrl()).isEqualTo(requestedUrl);
        assertThat(snapshot.getFinalUrl()).isEqualTo(finalUrl);
        assertThat(snapshot.getHttpStatus()).isEqualTo(200);
        assertThat(snapshot.getEtag()).isEqualTo("etag-1");
    }

    @Test
    void runPipeline_keepsUrlImportStepProgressionAndSuccessHandling() {
        UUID jobId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        String canonicalUrl = "https://laws.example.vn/start";

        KbSource source = KbSource.builder()
                .id(sourceId)
                .sourceType(SourceType.WEBSITE_PAGE)
                .title("Traffic page")
                .build();
        KbSourceVersion version = KbSourceVersion.builder()
                .id(versionId)
                .source(source)
                .canonicalUrl(canonicalUrl)
                .processingVersion("1.0")
                .build();
        KbIngestionJob job = KbIngestionJob.builder()
                .id(jobId)
                .source(source)
                .sourceVersionId(versionId)
                .jobType(JobType.URL_IMPORT)
                .status(IngestionJobStatus.QUEUED)
                .build();
        FetchResult fetchResult = new FetchResult(
                canonicalUrl,
                "https://laws.example.vn/final",
                200,
                "text/html",
                "Traffic page",
                "<html><body>Noi dung</body></html>",
                null,
                null
        );
        ParsedDocument parsedDocument = new ParsedDocument(
                "Noi dung",
                "Traffic page",
                "text/html",
                "jsoup",
                "1.19.1",
                List.of(new ParsedDocument.PageSection(1, "full", "Noi dung"))
        );

        List<IngestionStep> savedSteps = new ArrayList<>();
        List<IngestionJobStatus> savedStatuses = new ArrayList<>();

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(versionRepo.findById(versionId)).thenReturn(Optional.of(version));
        when(safeUrlFetcher.fetch(canonicalUrl)).thenReturn(fetchResult);
        when(urlPageParser.parseFetchedPage(fetchResult)).thenReturn(parsedDocument);
        when(tokenChunkingService.chunk(parsedDocument, sourceId.toString(), versionId.toString(), "1.0"))
                .thenReturn(List.of());
        when(versionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            KbIngestionJob saved = invocation.getArgument(0);
            savedSteps.add(saved.getStepName());
            savedStatuses.add(saved.getStatus());
            return saved;
        }).when(jobRepo).save(any());

        orchestrator.runPipeline(jobId).join();

        assertThat(savedSteps).contains(IngestionStep.FETCH, IngestionStep.PARSE, IngestionStep.CHUNK,
                IngestionStep.EMBED, IngestionStep.INDEX, IngestionStep.FINALIZE);
        assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.SUCCEEDED);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(version.getIndexStatus()).isEqualTo("INDEXED");
        assertThat(savedStatuses).contains(IngestionJobStatus.RUNNING, IngestionJobStatus.SUCCEEDED);
    }
}
