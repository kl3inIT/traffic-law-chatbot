package com.vn.traffic.chatbot.ingestion.fetch;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        private final HttpClient httpClient = buildHttpClient();

        private static HttpClient buildHttpClient() {
            try {
                return HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(FETCH_TIMEOUT_MS))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .sslContext(buildClientSslContext())
                        .build();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to initialize HTTP client", ex);
            }
        }

        private static SSLContext buildClientSslContext() throws Exception {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{buildTrustManager()}, new SecureRandom());
            return sslContext;
        }

        private static X509TrustManager buildTrustManager() throws Exception {
            List<X509TrustManager> trustManagers = new ArrayList<>();
            trustManagers.add(loadTrustManager(null));
            if (isWindows()) {
                try {
                    trustManagers.add(loadTrustManager("Windows-ROOT"));
                } catch (Exception ex) {
                    log.info("Windows root trust store unavailable, using default JVM trust store only: {}", ex.getMessage());
                }
            }
            return new CompositeX509TrustManager(trustManagers);
        }

        private static X509TrustManager loadTrustManager(String keyStoreType) throws Exception {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = keyStoreType == null ? null : KeyStore.getInstance(keyStoreType);
            if (keyStore != null) {
                keyStore.load(null, null);
            }
            factory.init(keyStore);
            for (TrustManager trustManager : factory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager x509TrustManager) {
                    return x509TrustManager;
                }
            }
            throw new IllegalStateException("No X509TrustManager available for keyStoreType=" + keyStoreType);
        }

        private static boolean isWindows() {
            String osName = System.getProperty("os.name", "");
            return osName.toLowerCase().contains("win");
        }

        @Override
        public RawFetchResponse execute(String url) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(FETCH_TIMEOUT_MS))
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("User-Agent", "traffic-law-chatbot/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new RawFetchResponse(
                    response.uri().toString(),
                    response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(null),
                    response.body(),
                    response.headers().map().entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
        }
    }

    static class CompositeX509TrustManager implements X509TrustManager {
        private final List<X509TrustManager> delegates;

        CompositeX509TrustManager(List<X509TrustManager> delegates) {
            this.delegates = List.copyOf(delegates);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            java.security.cert.CertificateException last = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    delegate.checkClientTrusted(chain, authType);
                    return;
                } catch (java.security.cert.CertificateException ex) {
                    last = ex;
                }
            }
            if (last != null) {
                throw last;
            }
            throw new java.security.cert.CertificateException("No trust managers configured");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            java.security.cert.CertificateException last = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    delegate.checkServerTrusted(chain, authType);
                    return;
                } catch (java.security.cert.CertificateException ex) {
                    last = ex;
                }
            }
            if (last != null) {
                throw last;
            }
            throw new java.security.cert.CertificateException("No trust managers configured");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegates.stream()
                    .flatMap(delegate -> List.of(delegate.getAcceptedIssuers()).stream())
                    .toArray(X509Certificate[]::new);
        }
    }
}
