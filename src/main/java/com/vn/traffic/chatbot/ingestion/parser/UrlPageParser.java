package com.vn.traffic.chatbot.ingestion.parser;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/**
 * JSoup-backed single-URL page importer with SSRF host validation.
 * T-03-01: Validates the parsed host against InetAddress loopback, link-local,
 * and site-local checks before making any HTTP connection.
 */
@Component
@Slf4j
public class UrlPageParser {

    private static final String PARSER_NAME = "jsoup";
    private static final String PARSER_VERSION = "1.19.1";
    private static final int FETCH_TIMEOUT_MS = 15_000;

    /**
     * Validate the URL's host for SSRF without fetching.
     * Throws AppException(URL_NOT_ALLOWED) for private/internal addresses.
     */
    public void validateHost(String rawUrl) {
        URI uri = parseUri(rawUrl);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new AppException(ErrorCode.URL_NOT_ALLOWED,
                    "Only http and https schemes are permitted: " + rawUrl);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new AppException(ErrorCode.URL_NOT_ALLOWED, "No host in URL: " + rawUrl);
        }
        assertHostNotPrivate(host);
    }

    public ParsedDocument fetchAndParse(String rawUrl) {
        // SSRF prevention — validate before any HTTP connection
        validateHost(rawUrl);

        try {
            org.jsoup.Connection connection = Jsoup.connect(rawUrl).timeout(FETCH_TIMEOUT_MS);
            Document doc = connection.get();
            String title = doc.title();
            String bodyText = doc.body().text();
            String finalUrl = connection.response().url().toString();

            ParsedDocument.PageSection section = new ParsedDocument.PageSection(1, "full", bodyText);
            return new ParsedDocument(bodyText, title, "text/html", PARSER_NAME, PARSER_VERSION,
                    List.of(section));
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("URL fetch/parse failed for url={}: {}", rawUrl, ex.getMessage());
            throw new AppException(ErrorCode.INGESTION_FAILED, "URL fetch failed: " + ex.getMessage());
        }
    }

    private void assertHostNotPrivate(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress()) {
                throw new AppException(ErrorCode.URL_NOT_ALLOWED,
                        "Private/internal addresses not permitted: " + host);
            }
            if (address.isLinkLocalAddress()) {
                throw new AppException(ErrorCode.URL_NOT_ALLOWED,
                        "Private/internal addresses not permitted: " + host);
            }
            if (address.isSiteLocalAddress()) {
                throw new AppException(ErrorCode.URL_NOT_ALLOWED,
                        "Private/internal addresses not permitted: " + host);
            }
            // Block 169.254.0.0/16 (APIPA / AWS metadata)
            byte[] addr = address.getAddress();
            if (addr.length == 4 && (addr[0] & 0xFF) == 169 && (addr[1] & 0xFF) == 254) {
                throw new AppException(ErrorCode.URL_NOT_ALLOWED,
                        "Private/internal addresses not permitted: " + host);
            }
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Host resolution failed for host={}: {}", host, ex.getMessage());
            throw new AppException(ErrorCode.URL_NOT_ALLOWED,
                    "Cannot resolve host: " + host);
        }
    }

    private URI parseUri(String rawUrl) {
        try {
            return URI.create(rawUrl);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.URL_NOT_ALLOWED, "Malformed URL: " + rawUrl);
        }
    }
}
