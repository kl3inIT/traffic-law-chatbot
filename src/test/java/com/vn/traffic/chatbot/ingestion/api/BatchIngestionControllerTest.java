package com.vn.traffic.chatbot.ingestion.api;

import com.vn.traffic.chatbot.ingestion.api.dto.BatchImportItemRequest;
import com.vn.traffic.chatbot.ingestion.api.dto.BatchImportItemResult;
import com.vn.traffic.chatbot.ingestion.api.dto.BatchImportRequest;
import com.vn.traffic.chatbot.ingestion.api.dto.BatchImportResponse;
import com.vn.traffic.chatbot.ingestion.service.IngestionService;
import com.vn.traffic.chatbot.source.domain.SourceType;
import com.vn.traffic.chatbot.source.domain.TrustTier;
import com.vn.traffic.chatbot.source.repo.KbSourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Wave 0 RED stubs — these tests will fail until T2 implements the batch endpoint.
 *
 * <p>Tests cover:
 * 1. A valid single-item batch returns ACCEPTED status.
 * 2. A duplicate URL in a subsequent call returns DUPLICATE status (D-13: includes PRIMARY-tier item).
 */
@ExtendWith(MockitoExtension.class)
class BatchIngestionControllerTest {

    @Mock
    private IngestionService ingestionService;

    @Mock
    private KbSourceRepository kbSourceRepository;

    @InjectMocks
    private IngestionAdminController controller;

    @Test
    void batchImport_singleItem_returnsAccepted() {
        // Arrange
        var item = new BatchImportItemRequest(
                "https://thuvienphapluat.vn/van-ban/giao-thong",
                "Luat Giao Thong",
                SourceType.WEBSITE_PAGE,
                TrustTier.PRIMARY   // D-13: trustCategory included for PRIMARY-tier test
        );
        var request = new BatchImportRequest(List.of(item));

        when(kbSourceRepository.findByOriginValue(eq("https://thuvienphapluat.vn/van-ban/giao-thong")))
                .thenReturn(Optional.empty());

        var acceptedResponse = new com.vn.traffic.chatbot.ingestion.api.dto.IngestionAcceptedResponse(
                UUID.randomUUID(), UUID.randomUUID(), "QUEUED");
        when(ingestionService.submitUrl(any())).thenReturn(acceptedResponse);

        // Act
        var response = controller.batchImport(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        BatchImportResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.results()).hasSize(1);
        assertThat(body.results().get(0).status()).isIn("ACCEPTED", "DUPLICATE");
        assertThat(body.accepted()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void duplicateUrl_returnsStatusDuplicate() {
        // Arrange — same URL submitted as already-existing source
        var url = "https://thuvienphapluat.vn/van-ban/giao-thong";
        var item = new BatchImportItemRequest(
                url,
                "Luat Giao Thong",
                SourceType.WEBSITE_PAGE,
                TrustTier.PRIMARY
        );
        var request = new BatchImportRequest(List.of(item));

        var existingSource = com.vn.traffic.chatbot.source.domain.KbSource.builder()
                .id(UUID.randomUUID())
                .build();
        when(kbSourceRepository.findByOriginValue(url)).thenReturn(Optional.of(existingSource));

        // Act
        var response = controller.batchImport(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        BatchImportResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.results()).hasSize(1);
        BatchImportItemResult result = body.results().get(0);
        assertThat(result.status()).isEqualTo("DUPLICATE");
        assertThat(result.url()).isEqualTo(url);
    }
}
