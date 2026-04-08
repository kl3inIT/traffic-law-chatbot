package com.vn.traffic.chatbot.ingestion.parser;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.ingestion.parser.springai.PdfDocumentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class FileIngestionParserResolver {

    private static final Set<String> TIKA_FALLBACK_MIME_TYPES = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final PdfDocumentParser pdfDocumentParser;
    private final TikaDocumentParser tikaDocumentParser;

    public DocumentParser resolve(String mimeType, String fileName) {
        if (pdfDocumentParser.isSupported(mimeType, fileName)) {
            return pdfDocumentParser;
        }
        if (isTikaFallbackAllowed(mimeType, fileName)) {
            return tikaDocumentParser;
        }
        throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Unsupported ingestion format: " + describeFormat(mimeType, fileName));
    }

    private boolean isTikaFallbackAllowed(String mimeType, String fileName) {
        if (mimeType != null && TIKA_FALLBACK_MIME_TYPES.contains(mimeType.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".doc") || normalized.endsWith(".docx");
    }

    private String describeFormat(String mimeType, String fileName) {
        if (mimeType != null && !mimeType.isBlank()) {
            return mimeType;
        }
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        return "unknown";
    }
}
