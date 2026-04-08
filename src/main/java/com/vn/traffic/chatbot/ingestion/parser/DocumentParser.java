package com.vn.traffic.chatbot.ingestion.parser;

import com.vn.traffic.chatbot.ingestion.fetch.FetchResult;

import java.io.InputStream;

public interface DocumentParser {
    ParsedDocument parse(InputStream content, String mimeType, String fileName);

    default ParsedDocument parse(FetchResult fetchResult) {
        throw new UnsupportedOperationException("FetchResult parsing is not supported by this parser");
    }
}
