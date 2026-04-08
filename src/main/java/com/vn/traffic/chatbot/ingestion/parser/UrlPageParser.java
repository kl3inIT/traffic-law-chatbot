package com.vn.traffic.chatbot.ingestion.parser;

import com.vn.traffic.chatbot.ingestion.fetch.FetchResult;
import com.vn.traffic.chatbot.ingestion.fetch.SafeUrlFetcher;
import com.vn.traffic.chatbot.ingestion.parser.springai.HtmlDocumentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Domain-facing URL parser facade.
 * SSRF validation remains in SafeUrlFetcher while HTML parsing is delegated behind the parser boundary.
 */
@Component
@RequiredArgsConstructor
public class UrlPageParser {

    private final SafeUrlFetcher safeUrlFetcher;
    private final HtmlDocumentParser htmlDocumentParser;

    public void validateHost(String rawUrl) {
        safeUrlFetcher.validateHost(rawUrl);
    }

    public ParsedDocument fetchAndParse(String rawUrl) {
        return parseFetchedPage(safeUrlFetcher.fetch(rawUrl));
    }

    public ParsedDocument parseFetchedPage(FetchResult fetchResult) {
        return htmlDocumentParser.parse(fetchResult);
    }
}
