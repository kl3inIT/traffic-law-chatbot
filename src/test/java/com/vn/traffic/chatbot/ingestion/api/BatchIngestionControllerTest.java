package com.vn.traffic.chatbot.ingestion.api;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.ingestion.api.dto.BatchImportItemRequest;
import com.vn.traffic.chatbot.ingestion.api.dto.BatchImportItemResult;
import com.vn.traffic.chatbot.ingestion.api.dto.BatchImportRequest;
import com.vn.traffic.chatbot.ingestion.api.dto.BatchImportResponse;
import com.vn.traffic.chatbot.ingestion.service.IngestionService;
import com.vn.traffic.chatbot.source.domain.SourceType;
import com.vn.traffic.chatbot.source.domain.TrustTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the batch ingestion endpoint.
 *
 * <p>Duplicate detection is delegated to {@link IngestionService}, which throws
 * {@code AppException(DUPLICATE_SOURCE)} when a URL already exists. The controller
 * translates this into a DUPLICATE result row — tested here without any repository mock.
 */
@ExtendWith(MockitoExtension.class)
class BatchIngestionControllerTest {

    @Mock
    private IngestionService ingestionService;

    @InjectMocks
    private IngestionAdminController controller;

    @Test
    void batchImport_singleItem_returnsAccepted() {
        // Arrange
        var item = new BatchImportItemRequest(
                "https://thuvienphapluat.vn/van-ban/giao-thong",
                "Luat Giao Thong",
                SourceType.WEBSITE_PAGE,
                TrustTier.PRIMARY
        );
        var request = new BatchImportRequest(List.of(item));

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
        assertThat(body.results().get(0).status()).isEqualTo("ACCEPTED");
        assertThat(body.accepted()).isEqualTo(1);
    }

    @Test
    void duplicateUrl_returnsStatusDuplicate() {
        // Arrange — service throws DUPLICATE_SOURCE when URL already exists
        var url = "https://thuvienphapluat.vn/van-ban/giao-thong";
        var item = new BatchImportItemRequest(
                url,
                "Luat Giao Thong",
                SourceType.WEBSITE_PAGE,
                TrustTier.PRIMARY
        );
        var request = new BatchImportRequest(List.of(item));

        when(ingestionService.submitUrl(any()))
                .thenThrow(new AppException(ErrorCode.DUPLICATE_SOURCE, "Duplicate source URL: " + url));

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
