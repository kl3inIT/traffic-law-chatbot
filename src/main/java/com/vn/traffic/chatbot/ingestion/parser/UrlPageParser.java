package com.vn.traffic.chatbot.ingestion.parser;

import com.vn.traffic.chatbot.ingestion.fetch.FetchResult;
import com.vn.traffic.chatbot.ingestion.fetch.SafeUrlFetcher;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JSoup-backed single-URL page parser that consumes already-fetched content.
 * SSRF validation remains in SafeUrlFetcher.
 */
@Component
@RequiredArgsConstructor
public class UrlPageParser {

    private static final String PARSER_NAME = "jsoup";
    private static final String PARSER_VERSION = "1.19.1";

    private final SafeUrlFetcher safeUrlFetcher;

    public void validateHost(String rawUrl) {
        safeUrlFetcher.validateHost(rawUrl);
    }

    public ParsedDocument fetchAndParse(String rawUrl) {
        return parseFetchedPage(safeUrlFetcher.fetch(rawUrl));
    }

    public ParsedDocument parseFetchedPage(FetchResult fetchResult) {
        Document doc = Jsoup.parse(fetchResult.body(), fetchResult.finalUrl());
        String title = doc.title();
        String bodyText = doc.body() != null ? doc.body().text() : doc.text();
        if ((title == null || title.isBlank()) && fetchResult.titleHint() != null) {
            title = fetchResult.titleHint();
        }

        ParsedDocument.PageSection section = new ParsedDocument.PageSection(1, "full", bodyText);
        return new ParsedDocument(bodyText, title, fetchResult.contentType(), PARSER_NAME, PARSER_VERSION,
                List.of(section));
    }
}
