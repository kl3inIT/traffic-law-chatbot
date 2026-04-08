package com.vn.traffic.chatbot.ingestion.orchestrator;

import com.vn.traffic.chatbot.ingestion.domain.IngestionJobStatus;
import com.vn.traffic.chatbot.ingestion.domain.IngestionStep;
import com.vn.traffic.chatbot.ingestion.domain.KbIngestionJob;
import com.vn.traffic.chatbot.ingestion.chunking.ChunkResult;
import com.vn.traffic.chatbot.ingestion.chunking.TokenChunkingService;
import com.vn.traffic.chatbot.ingestion.fetch.FetchResult;
import com.vn.traffic.chatbot.ingestion.fetch.SafeUrlFetcher;
import com.vn.traffic.chatbot.ingestion.parser.ParsedDocument;
import com.vn.traffic.chatbot.ingestion.parser.FileIngestionParserResolver;
import com.vn.traffic.chatbot.ingestion.parser.UrlPageParser;
import com.vn.traffic.chatbot.ingestion.repo.KbIngestionJobRepository;
import com.vn.traffic.chatbot.ingestion.repo.KbSourceFetchSnapshotRepository;
import com.vn.traffic.chatbot.ingestion.domain.KbSourceFetchSnapshot;
import com.vn.traffic.chatbot.source.domain.KbSource;
import com.vn.traffic.chatbot.source.domain.KbSourceVersion;
import com.vn.traffic.chatbot.source.repo.KbSourceVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Async ingestion pipeline orchestrator.
 * Transitions job state through FETCH -> PARSE -> CHUNK -> EMBED/INDEX -> FINALIZE.
 * T-03-02: buildMetadata() hardcodes trusted="false" and active="false" for all new chunks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class IngestionOrchestrator {

    private final KbIngestionJobRepository jobRepo;
    private final KbSourceVersionRepository versionRepo;
    private final KbSourceFetchSnapshotRepository fetchSnapshotRepo;
    private final FileIngestionParserResolver fileIngestionParserResolver;
    private final UrlPageParser urlPageParser;
    private final SafeUrlFetcher safeUrlFetcher;
    private final TokenChunkingService tokenChunkingService;
    private final VectorStore vectorStore;

    @Async("ingestionExecutor")
    public CompletableFuture<Void> runPipeline(UUID jobId) {
        KbIngestionJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        try {
            KbSourceVersion version = versionRepo.findById(job.getSourceVersionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Version not found: " + job.getSourceVersionId()));
            KbSource source = version.getSource();

            // --- FETCH ---
            transitionStep(job, IngestionStep.FETCH);
            ParsedDocument parsedDoc;

            switch (job.getJobType()) {
                case URL_IMPORT -> {
                    FetchResult fetchResult = safeUrlFetcher.fetch(version.getCanonicalUrl());
                    fetchSnapshotRepo.save(KbSourceFetchSnapshot.builder()
                            .sourceVersionId(version.getId())
                            .requestedUrl(fetchResult.requestedUrl())
                            .finalUrl(fetchResult.finalUrl())
                            .httpStatus(fetchResult.httpStatus())
                            .etag(fetchResult.etag())
                            .lastModified(fetchResult.lastModified())
                            .build());
                    transitionStep(job, IngestionStep.PARSE);
                    parsedDoc = urlPageParser.parseFetchedPage(fetchResult);
                    version.setParserName(parsedDoc.parserName());
                    version.setParserVersion(parsedDoc.parserVersion());
                    version.setMimeType(parsedDoc.mimeType());
                    versionRepo.save(version);
                }
                case FILE_UPLOAD, REINGEST -> {
                    InputStream inputStream = loadFileStream(version.getStorageUri(),
                            version.getMimeType(), version.getFileName());
                    // --- PARSE ---
                    transitionStep(job, IngestionStep.PARSE);
                    parsedDoc = fileIngestionParserResolver.resolve(version.getMimeType(), version.getFileName())
                            .parse(inputStream, version.getMimeType(), version.getFileName());
                    version.setParserName(parsedDoc.parserName());
                    version.setParserVersion(parsedDoc.parserVersion());
                    versionRepo.save(version);
                }
                default -> throw new IllegalArgumentException("Unknown job type: " + job.getJobType());
            }

            // --- CHUNK ---
            transitionStep(job, IngestionStep.CHUNK);
            String processingVersion = version.getProcessingVersion() != null
                    ? version.getProcessingVersion() : "1.0";
            List<ChunkResult> chunks = tokenChunkingService.chunk(parsedDoc,
                    source.getId().toString(),
                    version.getId().toString(),
                    processingVersion);
            version.setChunkingStrategy(tokenChunkingService.strategy());
            version.setChunkingVersion(tokenChunkingService.version());
            versionRepo.save(version);

            // --- EMBED + INDEX ---
            transitionStep(job, IngestionStep.EMBED);
            List<Document> documents = chunks.stream()
                    .map(c -> new Document(c.text(), buildMetadata(c, source)))
                    .collect(Collectors.toList());
            vectorStore.add(documents);

            transitionStep(job, IngestionStep.INDEX);

            // --- FINALIZE ---
            transitionStep(job, IngestionStep.FINALIZE);
            job.setStatus(IngestionJobStatus.SUCCEEDED);
            job.setFinishedAt(OffsetDateTime.now());
            version.setIndexStatus("INDEXED");
            versionRepo.save(version);
            jobRepo.save(job);

            log.info("Ingestion pipeline completed for jobId={}, chunks={}", jobId, chunks.size());

        } catch (Exception ex) {
            log.error("Ingestion pipeline failed for jobId={}: {}", jobId, ex.getMessage(), ex);
            job.setStatus(IngestionJobStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            job.setFinishedAt(OffsetDateTime.now());
            jobRepo.save(job);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void transitionStep(KbIngestionJob job, IngestionStep step) {
        job.setStepName(step);
        job.setStatus(IngestionJobStatus.RUNNING);
        if (job.getStartedAt() == null) {
            job.setStartedAt(OffsetDateTime.now());
        }
        jobRepo.save(job);
    }

    /**
     * Build vector store document metadata.
     * T-03-02: trusted and active are hardcoded to "false" — only changed by
     * explicit SourceService.activate() after manual admin approval.
     */
    Map<String, Object> buildMetadata(ChunkResult c, KbSource s) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("sourceId", s.getId().toString());
        meta.put("sourceVersionId", c.sourceVersionId());
        meta.put("sourceType", s.getSourceType().name());
        meta.put("trusted", "false");
        meta.put("active", "false");
        meta.put("approvalState", s.getApprovalState().name());
        meta.put("origin", s.getOriginValue());
        meta.put("locationType", c.pageNumber() > 0 ? "page" : "section");
        meta.put("pageNumber", c.pageNumber());
        meta.put("sectionRef", c.sectionRef());
        meta.put("contentHash", c.contentHash());
        meta.put("processingVersion", c.processingVersion());
        meta.put("chunkOrdinal", c.chunkOrdinal());
        return meta;
    }

    private InputStream loadFileStream(String storageUri, String mimeType, String fileName) {
        if (storageUri == null || storageUri.isBlank()) {
            return new ByteArrayInputStream(new byte[0]);
        }
        try {
            return new FileInputStream(storageUri);
        } catch (Exception ex) {
            log.warn("Could not load file from storageUri={}: {}", storageUri, ex.getMessage());
            return new ByteArrayInputStream(new byte[0]);
        }
    }
}
