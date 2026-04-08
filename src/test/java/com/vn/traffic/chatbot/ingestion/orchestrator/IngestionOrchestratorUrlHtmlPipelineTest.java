package com.vn.traffic.chatbot.ingestion.orchestrator;

import com.vn.traffic.chatbot.ingestion.chunking.ChunkResult;
import com.vn.traffic.chatbot.ingestion.chunking.TextChunker;
import com.vn.traffic.chatbot.ingestion.domain.IngestionJobStatus;
import com.vn.traffic.chatbot.ingestion.domain.JobType;
import com.vn.traffic.chatbot.ingestion.domain.KbIngestionJob;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionOrchestratorUrlHtmlPipelineTest {

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
    private TextChunker textChunker;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private IngestionOrchestrator orchestrator;

    @Test
    void runPipeline_usesSafeFetchThenSpringAiHtmlParsingThenChunkingAndIndexing() {
        PipelineFixture fixture = arrangeFixture();

        orchestrator.runPipeline(fixture.job().getId()).join();

        InOrder inOrder = inOrder(safeUrlFetcher, fetchSnapshotRepo, urlPageParser, textChunker, vectorStore);
        inOrder.verify(safeUrlFetcher).fetch(fixture.version().getCanonicalUrl());
        inOrder.verify(fetchSnapshotRepo).save(any());
        inOrder.verify(urlPageParser).parseFetchedPage(fixture.fetchResult());
        inOrder.verify(textChunker).chunk(fixture.parsedDocument(), fixture.source().getId().toString(), fixture.version().getId().toString(), "1.0");
        inOrder.verify(vectorStore).add(any());
    }

    @Test
    void runPipeline_keepsVectorMetadataCompatibleForInspectionAndRetrieval() {
        PipelineFixture fixture = arrangeFixture();

        orchestrator.runPipeline(fixture.job().getId()).join();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        Document indexedDocument = documentsCaptor.getValue().getFirst();

        assertThat(indexedDocument.getText()).isEqualTo("Nội dung chunk");
        assertThat(indexedDocument.getMetadata()).containsEntry("sourceId", fixture.source().getId().toString());
        assertThat(indexedDocument.getMetadata()).containsEntry("sourceVersionId", fixture.version().getId().toString());
        assertThat(indexedDocument.getMetadata()).containsEntry("origin", fixture.source().getOriginValue());
        assertThat(indexedDocument.getMetadata()).containsEntry("chunkOrdinal", 0);
        assertThat(indexedDocument.getMetadata()).containsEntry("processingVersion", "1.0");
        assertThat(indexedDocument.getMetadata()).containsEntry("approvalState", "PENDING");
        assertThat(indexedDocument.getMetadata()).containsEntry("trusted", "false");
        assertThat(indexedDocument.getMetadata()).containsEntry("active", "false");
    }

    @Test
    void runPipeline_preservesRetrievalFilterCompatibilityForNewUrlChunks() {
        PipelineFixture fixture = arrangeFixture();

        orchestrator.runPipeline(fixture.job().getId()).join();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        Document indexedDocument = documentsCaptor.getValue().getFirst();

        assertThat(indexedDocument.getMetadata()).containsEntry("approvalState", "PENDING");
        assertThat(indexedDocument.getMetadata()).containsEntry("trusted", "false");
        assertThat(indexedDocument.getMetadata()).containsEntry("active", "false");
    }

    @Test
    void runPipeline_persistsSpringAiParserIdentityForUrlImportsOnly() {
        PipelineFixture fixture = arrangeFixture();

        orchestrator.runPipeline(fixture.job().getId()).join();

        assertThat(fixture.version().getParserName()).isEqualTo("spring-ai-jsoup-reader");
        assertThat(fixture.version().getParserVersion()).isEqualTo("2.0.0-M4");
        assertThat(fixture.version().getMimeType()).isEqualTo("text/html");
        assertThat(fixture.version().getIndexStatus()).isEqualTo("INDEXED");
        assertThat(fixture.job().getStatus()).isEqualTo(IngestionJobStatus.SUCCEEDED);
    }

    private PipelineFixture arrangeFixture() {
        UUID sourceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String canonicalUrl = "https://example.vn/traffic-law";

        KbSource source = KbSource.builder()
                .id(sourceId)
                .sourceType(SourceType.WEBSITE_PAGE)
                .originValue(canonicalUrl)
                .approvalState(com.vn.traffic.chatbot.source.domain.ApprovalState.PENDING)
                .title("Traffic law page")
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
                canonicalUrl,
                200,
                "text/html",
                "Traffic law page",
                "<html><body>Nội dung đã fetch</body></html>",
                "etag-1",
                null
        );
        ParsedDocument parsedDocument = new ParsedDocument(
                "Nội dung đã parse",
                "Traffic law page",
                "text/html",
                "spring-ai-jsoup-reader",
                "2.0.0-M4",
                List.of(new ParsedDocument.PageSection(1, "full", "Nội dung đã parse"))
        );
        ChunkResult chunkResult = new ChunkResult(
                "Nội dung chunk",
                0,
                1,
                "full",
                "hash-1",
                "1.0",
                sourceId.toString(),
                versionId.toString()
        );

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(versionRepo.findById(versionId)).thenReturn(Optional.of(version));
        when(safeUrlFetcher.fetch(canonicalUrl)).thenReturn(fetchResult);
        when(urlPageParser.parseFetchedPage(fetchResult)).thenReturn(parsedDocument);
        when(textChunker.chunk(parsedDocument, sourceId.toString(), versionId.toString(), "1.0"))
                .thenReturn(List.of(chunkResult));
        when(versionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        return new PipelineFixture(source, version, job, fetchResult, parsedDocument);
    }

    private record PipelineFixture(
            KbSource source,
            KbSourceVersion version,
            KbIngestionJob job,
            FetchResult fetchResult,
            ParsedDocument parsedDocument
    ) {}
}
