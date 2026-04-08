package com.vn.traffic.chatbot.ingestion.fetch;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class SafeUrlFetcher {

    static final int FETCH_TIMEOUT_MS = 15_000;
    private static final int MAX_REDIRECTS = 5;

    private final HttpExecutor httpExecutor;
    private final HostResolver hostResolver;

    public SafeUrlFetcher() {
        this(new JsoupHttpExecutor(), InetAddress::getByName);
    }

    SafeUrlFetcher(HttpExecutor httpExecutor, HostResolver hostResolver) {
        this.httpExecutor = httpExecutor;
        this.hostResolver = hostResolver;
    }

    public void validateHost(String rawUrl) {
        URI uri = parseUri(rawUrl);
        validateScheme(uri, rawUrl);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new AppException(ErrorCode.URL_NOT_ALLOWED, "No host in URL: " + rawUrl);
        }
        assertHostNotPrivate(host);
    }

    public FetchResult fetch(String rawUrl) {
        validateHost(rawUrl);

        try {
            String requestedUrl = rawUrl;
            String currentUrl = rawUrl;
            RawFetchResponse response = null;

            for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
                response = httpExecutor.execute(currentUrl);
                String resolvedUrl = response.resolvedUrl();
                validateHost(resolvedUrl);

                if (!isRedirect(response.httpStatus())) {
                    currentUrl = resolvedUrl;
                    break;
                }

                String location = firstHeader(response.headers(), "Location");
                if (location == null || location.isBlank()) {
                    currentUrl = resolvedUrl;
                    break;
                }

                currentUrl = URI.create(resolvedUrl).resolve(location).toString();
                validateHost(currentUrl);

                if (redirectCount == MAX_REDIRECTS) {
                    throw new AppException(ErrorCode.INGESTION_FAILED,
                            "Too many redirects for URL: " + rawUrl);
                }
            }

            if (response == null) {
                throw new AppException(ErrorCode.INGESTION_FAILED, "URL fetch failed: empty response");
            }

            String titleHint = extractTitle(response.body());
            return new FetchResult(
                    requestedUrl,
                    currentUrl,
                    response.httpStatus(),
                    response.contentType(),
                    titleHint,
                    response.body(),
                    firstHeader(response.headers(), "ETag"),
                    parseLastModified(firstHeader(response.headers(), "Last-Modified"))
            );
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Safe fetch failed for url={}: {}", rawUrl, ex.getMessage());
            throw new AppException(ErrorCode.INGESTION_FAILED, "URL fetch failed: " + ex.getMessage());
        }
    }

    private void validateScheme(URI uri, String rawUrl) {
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new AppException(ErrorCode.URL_NOT_ALLOWED,
                    "Only http and https schemes are permitted: " + rawUrl);
        }
    }

    private void assertHostNotPrivate(String host) {
        try {
            InetAddress address = hostResolver.resolve(host);
            if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                throw new AppException(ErrorCode.URL_NOT_ALLOWED,
                        "Private/internal addresses not permitted: " + host);
            }
            byte[] addr = address.getAddress();
            if (addr.length == 4 && (addr[0] & 0xFF) == 169 && (addr[1] & 0xFF) == 254) {
                throw new AppException(ErrorCode.URL_NOT_ALLOWED,
                        "Private/internal addresses not permitted: " + host);
            }
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Host resolution failed for host={}: {}", host, ex.getMessage());
            throw new AppException(ErrorCode.URL_NOT_ALLOWED, "Cannot resolve host: " + host);
        }
    }

    private URI parseUri(String rawUrl) {
        try {
            return URI.create(rawUrl);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.URL_NOT_ALLOWED, "Malformed URL: " + rawUrl);
        }
    }

    private boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }

    private String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(values -> values != null && !values.isEmpty())
                .map(values -> values.getFirst())
                .findFirst()
                .orElse(null);
    }

    private String extractTitle(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Document document = Jsoup.parse(body);
            String title = document.title();
            return title == null || title.isBlank() ? null : title;
        } catch (Exception ex) {
            return null;
        }
    }

    private OffsetDateTime parseLastModified(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toOffsetDateTime();
        } catch (Exception ex) {
            return null;
        }
    }

    @FunctionalInterface
    interface HttpExecutor {
        RawFetchResponse execute(String url) throws Exception;
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress resolve(String host) throws Exception;
    }

    public record RawFetchResponse(
            String resolvedUrl,
            int httpStatus,
            String contentType,
            String body,
            Map<String, List<String>> headers
    ) {}

    static class JsoupHttpExecutor implements HttpExecutor {
        @Override
        public RawFetchResponse execute(String url) throws Exception {
            org.jsoup.Connection.Response response = Jsoup.connect(url)
                    .timeout(FETCH_TIMEOUT_MS)
                    .ignoreContentType(true)
                    .followRedirects(false)
                    .execute();
            return new RawFetchResponse(
                    response.url().toString(),
                    response.statusCode(),
                    response.contentType(),
                    response.body(),
                    response.multiHeaders()
            );
        }
    }
}
