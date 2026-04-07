package com.vn.traffic.chatbot.ingestion.parser;

import java.io.InputStream;

public interface DocumentParser {
    ParsedDocument parse(InputStream content, String mimeType, String fileName);
}
