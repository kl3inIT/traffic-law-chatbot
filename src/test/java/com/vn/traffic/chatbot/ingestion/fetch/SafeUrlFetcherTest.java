package com.vn.traffic.chatbot.ingestion.fetch;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeUrlFetcherTest {

    @Test
    void fetch_returnsRequestedAndFinalUrlWhenRedirectOccurs() throws Exception {
        String requestedUrl = "https://laws.example.vn/start";
        String finalUrl = "https://content.example.vn/final";

        SafeUrlFetcher fetcher = new SafeUrlFetcher(
                url -> switch (url) {
                    case "https://laws.example.vn/start" -> new SafeUrlFetcher.RawFetchResponse(
                            requestedUrl,
                            302,
                            "text/html",
                            "",
                            Map.of("Location", List.of(finalUrl))
                    );
                    case "https://content.example.vn/final" -> new SafeUrlFetcher.RawFetchResponse(
                            finalUrl,
                            200,
                            "text/html; charset=UTF-8",
                            "<html><head><title>Luat giao thong</title></head><body>Noi dung</body></html>",
                            Map.of(
                                    "ETag", List.of("\"abc123\""),
                                    "Last-Modified", List.of("Wed, 21 Oct 2015 07:28:00 GMT")
                            )
                    );
                    default -> throw new IllegalArgumentException("Unexpected URL: " + url);
                },
                host -> publicAddress(host)
        );

        FetchResult result = fetcher.fetch(requestedUrl);

        assertThat(result.requestedUrl()).isEqualTo(requestedUrl);
        assertThat(result.finalUrl()).isEqualTo(finalUrl);
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.contentType()).isEqualTo("text/html; charset=UTF-8");
        assertThat(result.titleHint()).isEqualTo("Luat giao thong");
        assertThat(result.body()).contains("Noi dung");
        assertThat(result.etag()).isEqualTo("\"abc123\"");
        assertThat(result.lastModified()).isEqualTo(OffsetDateTime.parse("2015-10-21T07:28:00Z"));
    }

    @Test
    void fetch_rejectsUnsafeOrMalformedHostsBeforeAnyHttpRequest() {
        AtomicInteger requestCount = new AtomicInteger();
        SafeUrlFetcher fetcher = new SafeUrlFetcher(
                url -> {
                    requestCount.incrementAndGet();
                    throw new IllegalStateException("Should not reach HTTP executor");
                },
                host -> switch (host) {
                    case "localhost" -> InetAddress.getByAddress(host, new byte[]{127, 0, 0, 1});
                    case "169.254.169.254" -> InetAddress.getByAddress(host, new byte[]{(byte) 169, (byte) 254, (byte) 169, (byte) 254});
                    case "192.168.1.10" -> InetAddress.getByAddress(host, new byte[]{(byte) 192, (byte) 168, 1, 10});
                    default -> publicAddress(host);
                }
        );

        assertUrlNotAllowed(fetcher, "http://localhost/admin");
        assertUrlNotAllowed(fetcher, "http://169.254.169.254/latest/meta-data");
        assertUrlNotAllowed(fetcher, "http://192.168.1.10/internal");
        assertUrlNotAllowed(fetcher, "ftp://example.com/file.txt");
        assertUrlNotAllowed(fetcher, "not-a-url");

        assertThat(requestCount.get()).isZero();
    }

    @Test
    void fetch_exposesResponseMetadataWithoutLeakingSpringAiTypes() throws Exception {
        String requestedUrl = "https://laws.example.vn/article";
        SafeUrlFetcher fetcher = new SafeUrlFetcher(
                url -> new SafeUrlFetcher.RawFetchResponse(
                        requestedUrl,
                        200,
                        "text/html",
                        "<html><head><title>Article</title></head><body>Body text</body></html>",
                        Map.of("ETag", List.of("etag-1"))
                ),
                host -> publicAddress(host)
        );

        FetchResult result = fetcher.fetch(requestedUrl);

        assertThat(result.requestedUrl()).isEqualTo(requestedUrl);
        assertThat(result.finalUrl()).isEqualTo(requestedUrl);
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.contentType()).isEqualTo("text/html");
        assertThat(result.titleHint()).isEqualTo("Article");
        assertThat(result.body()).contains("Body text");
        assertThat(result.etag()).isEqualTo("etag-1");
    }

    private static void assertUrlNotAllowed(SafeUrlFetcher fetcher, String url) {
        assertThatThrownBy(() -> fetcher.fetch(url))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.URL_NOT_ALLOWED);
    }

    private static InetAddress publicAddress(String host) throws UnknownHostException {
        return InetAddress.getByAddress(host, new byte[]{1, 1, 1, 1});
    }
}
