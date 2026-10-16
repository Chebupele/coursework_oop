package ru.oop.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.oop.aggregator.api.HttpApiClient;
import ru.oop.aggregator.model.ApiSource;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;

class HttpApiClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApiSource source = new ApiSource("test", URI.create("https://example.org/?q=java"));

    @Test
    void buildsRequestAndParsesResponseWithoutNetwork() throws Exception {
        FakeHttpClient http = new FakeHttpClient(200, "{\"nested\":{\"value\":7}}");
        var result = new HttpApiClient(http, mapper).fetch(source);
        assertEquals(7, result.at("/nested/value").asInt());
        assertEquals(source.url(), http.request.uri());
        assertEquals("GET", http.request.method());
        assertEquals("application/json", http.request.headers().firstValue("Accept").orElseThrow());
        assertEquals(Duration.ofSeconds(20), http.request.timeout().orElseThrow());
        assertTrue(new HttpApiClient(new FakeHttpClient(200, "[]"), mapper).fetch(source).isArray());
    }

    @Test
    void rejectsHttpErrorsInvalidJsonAndScalarResponses() {
        for (int code : new int[]{199, 301, 404, 500}) {
            IOException error = assertThrows(IOException.class,
                    () -> new HttpApiClient(new FakeHttpClient(code, "{}"), mapper).fetch(source));
            assertTrue(error.getMessage().contains("HTTP " + code));
        }
        for (String body : new String[]{"", "null", "42", "true", "broken"}) {
            assertThrows(IOException.class,
                    () -> new HttpApiClient(new FakeHttpClient(200, body), mapper).fetch(source));
        }
    }

    private static class FakeHttpClient extends HttpClient {
        private final int status;
        private final String body;
        private HttpRequest request;

        FakeHttpClient(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            this.request = request;
            return (HttpResponse<T>) new HttpResponse<String>() {
                public int statusCode() { return status; }
                public HttpRequest request() { return request; }
                public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
                public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
                public String body() { return body; }
                public Optional<SSLSession> sslSession() { return Optional.empty(); }
                public URI uri() { return request.uri(); }
                public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
            };
        }

        public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        public Optional<Duration> connectTimeout() { return Optional.empty(); }
        public Redirect followRedirects() { return Redirect.NEVER; }
        public Optional<ProxySelector> proxy() { return Optional.empty(); }
        public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        public SSLParameters sslParameters() { return new SSLParameters(); }
        public Optional<Authenticator> authenticator() { return Optional.empty(); }
        public Version version() { return Version.HTTP_1_1; }
        public Optional<Executor> executor() { return Optional.empty(); }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }
}
