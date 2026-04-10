package com.vn.traffic.chatbot.ingestion.orchestrator;

import com.vn.traffic.chatbot.ingestion.chunking.ChunkResult;
import com.vn.traffic.chatbot.ingestion.chunking.TokenChunkingService;
import com.vn.traffic.chatbot.ingestion.domain.IngestionJobStatus;
import com.vn.traffic.chatbot.ingestion.domain.JobType;
import com.vn.traffic.chatbot.ingestion.domain.KbIngestionJob;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.ingestion.parser.DocumentParser;
import com.vn.traffic.chatbot.ingestion.parser.FileIngestionParserResolver;
import com.vn.traffic.chatbot.ingestion.parser.ParsedDocument;
import com.vn.traffic.chatbot.ingestion.parser.UrlPageParser;
import com.vn.traffic.chatbot.ingestion.parser.springai.PdfDocumentParser;
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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionOrchestratorPdfParserSelectionTest {

    @Mock
    private KbIngestionJobRepository jobRepo;

    @Mock
    private KbSourceVersionRepository versionRepo;

    @Mock
    private KbSourceFetchSnapshotRepository fetchSnapshotRepo;

    @Mock
    private FileIngestionParserResolver fileIngestionParserResolver;

    @Mock
    private PdfDocumentParser pdfDocumentParser;

    @Mock
    private UrlPageParser urlPageParser;

    @Mock
    private TokenChunkingService tokenChunkingService;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private IngestionOrchestrator orchestrator;

    @Test
    void runPipeline_usesPdfDocumentParserForPdfWhenParityIsAvailable() {
        PipelineFixture fixture = arrangeFileFixture("application/pdf", "law.pdf");
        ParsedDocument springDoc = new ParsedDocument(
                "Điều 1\fĐiều 2",
                "law.pdf",
                "application/pdf",
                "spring-ai-pdf-reader",
                "2.0.0-M4",
                List.of(
                        new ParsedDocument.PageSection(1, "page-1", "Điều 1"),
                        new ParsedDocument.PageSection(2, "page-2", "Điều 2")
                )
        );
        ChunkResult chunk = new ChunkResult("Điều 1", 0, 1, "page-1", "hash-1", "1.0", fixture.source().getId().toString(), fixture.version().getId().toString());

        when(fileIngestionParserResolver.resolve("application/pdf", "law.pdf")).thenReturn(pdfDocumentParser);
        when(pdfDocumentParser.parse(any(InputStream.class), anyString(), anyString())).thenReturn(springDoc);
        when(tokenChunkingService.chunk(springDoc, fixture.source().getId().toString(), fixture.version().getId().toString(), "1.0"))
                .thenReturn(List.of(chunk));

        orchestrator.runPipeline(fixture.job().getId()).join();

        verify(pdfDocumentParser).parse(any(InputStream.class), anyString(), anyString());
        verify(fileIngestionParserResolver).resolve("application/pdf", "law.pdf");
        assertThat(fixture.version().getParserName()).isEqualTo("spring-ai-pdf-reader");
        assertThat(fixture.version().getParserVersion()).isEqualTo("2.0.0-M4");
        assertThat(fixture.version().getChunkingStrategy()).isEqualTo("spring-ai-token-text-splitter");
        assertThat(fixture.version().getChunkingVersion()).isEqualTo("2.0.0-M4");
    }

    @Test
    void runPipeline_fallsBackToTikaForNonPdfUploads() {
        PipelineFixture fixture = arrangeFileFixture("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "law.docx");
        ParsedDocument tikaDoc = new ParsedDocument(
                "Word content",
                "law.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "tika",
                "3.3.0",
                List.of(new ParsedDocument.PageSection(1, "full", "Word content"))
        );
        ChunkResult chunk = new ChunkResult("Word content", 0, 1, "full", "hash-1", "1.0", fixture.source().getId().toString(), fixture.version().getId().toString());

        DocumentParser tikaFallbackParser = (content, mimeType, fileName) -> tikaDoc;
        when(fileIngestionParserResolver.resolve("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "law.docx"))
                .thenReturn(tikaFallbackParser);
        when(tokenChunkingService.chunk(tikaDoc, fixture.source().getId().toString(), fixture.version().getId().toString(), "1.0"))
                .thenReturn(List.of(chunk));

        orchestrator.runPipeline(fixture.job().getId()).join();

        verify(fileIngestionParserResolver).resolve("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "law.docx");
        verify(pdfDocumentParser, never()).parse(any(InputStream.class), anyString(), anyString());
        assertThat(fixture.version().getParserName()).isEqualTo("tika");
        assertThat(fixture.version().getParserVersion()).isEqualTo("3.3.0");
    }

    @Test
    void runPipeline_fallsBackToTikaWhenPdfParitySupportIsUnavailable() {
        PipelineFixture fixture = arrangeFileFixture("application/pdf", "law.pdf");
        ParsedDocument tikaDoc = new ParsedDocument(
                "Điều 1\fĐiều 2",
                "law.pdf",
                "application/pdf",
                "tika",
                "3.3.0",
                List.of(
                        new ParsedDocument.PageSection(1, "page-1", "Điều 1"),
                        new ParsedDocument.PageSection(2, "page-2", "Điều 2")
                )
        );
        ChunkResult chunk = new ChunkResult("Điều 1", 0, 1, "page-1", "hash-1", "1.0", fixture.source().getId().toString(), fixture.version().getId().toString());

        DocumentParser tikaFallbackParser = (content, mimeType, fileName) -> tikaDoc;
        when(fileIngestionParserResolver.resolve("application/pdf", "law.pdf"))
                .thenReturn(tikaFallbackParser);
        when(tokenChunkingService.chunk(tikaDoc, fixture.source().getId().toString(), fixture.version().getId().toString(), "1.0"))
                .thenReturn(List.of(chunk));

        orchestrator.runPipeline(fixture.job().getId()).join();

        verify(fileIngestionParserResolver).resolve("application/pdf", "law.pdf");
        verify(pdfDocumentParser, never()).parse(any(InputStream.class), anyString(), anyString());
        assertThat(fixture.version().getParserName()).isEqualTo("tika");
    }

    @Test
    void runPipeline_rejectsUnsupportedFormatsWhenNoFallbackIsAllowed() {
        UUID sourceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        KbSource source = KbSource.builder()
                .id(sourceId)
                .sourceType(SourceType.STRUCTURED_REGULATION)
                .originValue("law.txt")
                .approvalState(com.vn.traffic.chatbot.source.domain.ApprovalState.PENDING)
                .title("law.txt")
                .build();
        KbSourceVersion version = KbSourceVersion.builder()
                .id(versionId)
                .source(source)
                .storageUri("")
                .mimeType("text/plain")
                .fileName("law.txt")
                .processingVersion("1.0")
                .build();
        KbIngestionJob job = KbIngestionJob.builder()
                .id(jobId)
                .source(source)
                .sourceVersionId(versionId)
                .jobType(JobType.FILE_UPLOAD)
                .status(IngestionJobStatus.QUEUED)
                .build();

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(versionRepo.findById(versionId)).thenReturn(Optional.of(version));
        when(jobRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileIngestionParserResolver.resolve("text/plain", "law.txt"))
                .thenThrow(new AppException(ErrorCode.VALIDATION_ERROR, "Unsupported ingestion format: text/plain"));

        orchestrator.runPipeline(jobId).join();

        assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("Unsupported ingestion format");
    }

    @Test
    void runPipeline_marksJobFailedWhenNoIndexableChunksAreProduced() {
        PipelineFixture fixture = arrangeFileFixture("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "law.docx");
        ParsedDocument tikaDoc = new ParsedDocument(
                "Word content",
                "law.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "tika",
                "3.3.0",
                List.of(new ParsedDocument.PageSection(1, "full", "Word content"))
        );

        DocumentParser tikaFallbackParser = (content, mimeType, fileName) -> tikaDoc;
        when(fileIngestionParserResolver.resolve("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "law.docx"))
                .thenReturn(tikaFallbackParser);
        when(tokenChunkingService.chunk(tikaDoc, fixture.source().getId().toString(), fixture.version().getId().toString(), "1.0"))
                .thenReturn(List.of());

        orchestrator.runPipeline(fixture.job().getId()).join();

        assertThat(fixture.job().getStatus()).isEqualTo(IngestionJobStatus.FAILED);
        assertThat(fixture.job().getErrorMessage()).contains("No indexable content extracted from source");
        verify(vectorStore, never()).add(any());
    }

    @Test
    void runPipeline_indexesChunkMetadataWithoutChangingExistingKeys() {
        PipelineFixture fixture = arrangeFileFixture("application/pdf", "law.pdf");
        ParsedDocument springDoc = new ParsedDocument(
                "Điều 1",
                "law.pdf",
                "application/pdf",
                "spring-ai-pdf-reader",
                "2.0.0-M4",
                List.of(new ParsedDocument.PageSection(1, "page-1", "Điều 1"))
        );
        ChunkResult chunk = new ChunkResult("Điều 1", 0, 1, "page-1", "hash-1", "1.0", fixture.source().getId().toString(), fixture.version().getId().toString());

        when(fileIngestionParserResolver.resolve("application/pdf", "law.pdf")).thenReturn(pdfDocumentParser);
        when(pdfDocumentParser.parse(any(InputStream.class), anyString(), anyString())).thenReturn(springDoc);
        when(tokenChunkingService.chunk(springDoc, fixture.source().getId().toString(), fixture.version().getId().toString(), "1.0"))
                .thenReturn(List.of(chunk));

        orchestrator.runPipeline(fixture.job().getId()).join();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        Document indexedDocument = documentsCaptor.getValue().getFirst();
        assertThat(indexedDocument.getMetadata()).containsEntry("chunkOrdinal", 0);
        assertThat(indexedDocument.getMetadata()).containsEntry("pageNumber", 1);
        assertThat(indexedDocument.getMetadata()).containsEntry("sectionRef", "page-1");
        assertThat(indexedDocument.getMetadata()).containsEntry("contentHash", "hash-1");
    }

    @Test
    void runPipeline_omitsNullMetadataValuesBeforeVectorStoreInsert() {
        PipelineFixture fixture = arrangeFileFixture("application/pdf", "law.pdf");
        fixture.source().setOriginValue(null);
        ParsedDocument springDoc = new ParsedDocument(
                "Điều 1",
                "law.pdf",
                "application/pdf",
                "spring-ai-pdf-reader",
                "2.0.0-M4",
                List.of(new ParsedDocument.PageSection(1, "page-1", "Điều 1"))
        );
        ChunkResult chunk = new ChunkResult("Điều 1", 0, 1, "page-1", "hash-1", "1.0", fixture.source().getId().toString(), fixture.version().getId().toString());

        when(fileIngestionParserResolver.resolve("application/pdf", "law.pdf")).thenReturn(pdfDocumentParser);
        when(pdfDocumentParser.parse(any(InputStream.class), anyString(), anyString())).thenReturn(springDoc);
        when(tokenChunkingService.chunk(springDoc, fixture.source().getId().toString(), fixture.version().getId().toString(), "1.0"))
                .thenReturn(List.of(chunk));

        orchestrator.runPipeline(fixture.job().getId()).join();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        Document indexedDocument = documentsCaptor.getValue().getFirst();
        assertThat(indexedDocument.getMetadata()).doesNotContainKey("origin");
        assertThat(indexedDocument.getMetadata()).containsEntry("pageNumber", 1);
    }

    private PipelineFixture arrangeFileFixture(String mimeType, String fileName) {
        UUID sourceId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        KbSource source = KbSource.builder()
                .id(sourceId)
                .sourceType(fileName.endsWith(".pdf") ? SourceType.PDF : SourceType.WORD)
                .originValue(fileName)
                .approvalState(com.vn.traffic.chatbot.source.domain.ApprovalState.PENDING)
                .title(fileName)
                .build();
        KbSourceVersion version = KbSourceVersion.builder()
                .id(versionId)
                .source(source)
                .storageUri("")
                .mimeType(mimeType)
                .fileName(fileName)
                .processingVersion("1.0")
                .build();
        KbIngestionJob job = KbIngestionJob.builder()
                .id(jobId)
                .source(source)
                .sourceVersionId(versionId)
                .jobType(JobType.FILE_UPLOAD)
                .status(IngestionJobStatus.QUEUED)
                .build();

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(versionRepo.findById(versionId)).thenReturn(Optional.of(version));
        when(tokenChunkingService.strategy()).thenReturn("spring-ai-token-text-splitter");
        when(tokenChunkingService.version()).thenReturn("2.0.0-M4");
        when(versionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        return new PipelineFixture(source, version, job);
    }

    private record PipelineFixture(KbSource source, KbSourceVersion version, KbIngestionJob job) {}
}
