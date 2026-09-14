package com.javascriptobfuscator.jso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests against a stubbed HttpClient. No real network calls.
 */
class JsoProtectorClientTest {

    /** Captures the outgoing request body and returns the canned response. */
    static class StubHttpClient extends HttpClient {
        String capturedBody;
        final String responseBody;
        final int responseStatus;

        StubHttpClient(String body, int status) {
            this.responseBody = body;
            this.responseStatus = status;
        }

        @Override public Optional<javax.net.ssl.SSLContext> sslContext() { return Optional.empty(); }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return null; }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
        @Override public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public java.net.http.HttpClient.Redirect followRedirects() { return java.net.http.HttpClient.Redirect.NEVER; }
        @Override public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }

        @SuppressWarnings("unchecked")
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            // Capture body
            try {
                java.util.concurrent.atomic.AtomicReference<String> bodyRef = new java.util.concurrent.atomic.AtomicReference<>("");
                request.bodyPublisher().ifPresent(pub -> {
                    java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> sub = new java.util.concurrent.Flow.Subscriber<>() {
                        java.util.concurrent.Flow.Subscription s;
                        final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription s) { this.s = s; s.request(Long.MAX_VALUE); }
                        @Override public void onNext(java.nio.ByteBuffer b) { byte[] arr = new byte[b.remaining()]; b.get(arr); buf.write(arr, 0, arr.length); }
                        @Override public void onError(Throwable t) { /* ignore */ }
                        @Override public void onComplete() { bodyRef.set(buf.toString(java.nio.charset.StandardCharsets.UTF_8)); }
                    };
                    pub.subscribe(sub);
                });
                this.capturedBody = bodyRef.get();
            } catch (Exception ignored) {}

            return new HttpResponse<>() {
                @Override public int statusCode() { return responseStatus; }
                @Override public HttpRequest request() { return request; }
                @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of("Content-Type", java.util.List.of("text/json")), (a, b) -> true); }
                @Override public T body() { return (T) responseBody; }
                @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return request.uri(); }
                @Override public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
            };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bh, HttpResponse.PushPromiseHandler<T> ph) {
            return sendAsync(request, bh);
        }
    }

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void label_propagates_as_release_label() throws Exception {
        StubHttpClient stub = new StubHttpClient(
                "{\"Type\":\"Succeed\",\"Items\":[{\"FileName\":\"app.js\",\"FileCode\":\"PROTECTED;\"}]," +
                        "\"Report\":{\"BuildId\":\"rel-1\",\"PolymorphismFingerprint\":\"abc123\"}}", 200);
        JsoProtectorClient client = new JsoProtectorClient(stub);

        ProtectResult result = client.protect(ProtectOptions.builder()
                .apiKey("k").apiPassword("p")
                .files(Map.of("app.js", "let x = 1;"))
                .preset("balanced")
                .label("ci-build-7f3a")
                .build());

        JsonNode body = M.readTree(stub.capturedBody);
        assertEquals("ci-build-7f3a", body.path("ReleaseLabel").asText());
        assertEquals("k", body.path("APIKey").asText());
        assertEquals("p", body.path("APIPwd").asText());
        assertTrue(body.path("FlatTransform").asBoolean());
        assertEquals("rel-1", result.buildId());
        assertEquals("abc123", result.polymorphismFingerprint());
        assertEquals("PROTECTED;", result.files().get("app.js"));
    }

    @Test
    void options_override_preset() throws Exception {
        StubHttpClient stub = new StubHttpClient(
                "{\"Type\":\"Succeed\",\"Items\":[{\"FileName\":\"x.js\",\"FileCode\":\"OK;\"}]}", 200);
        JsoProtectorClient client = new JsoProtectorClient(stub);

        client.protect(ProtectOptions.builder()
                .apiKey("k").apiPassword("p")
                .files(Map.of("x.js", "let y = 2;"))
                .preset("balanced")
                .optionOverride("FlatTransform", false)
                .optionOverride("LockDomain", true)
                .optionOverride("LockDomainList", "example.com")
                .build());

        JsonNode body = M.readTree(stub.capturedBody);
        assertFalse(body.path("FlatTransform").asBoolean());
        assertTrue(body.path("LockDomain").asBoolean());
        assertEquals("example.com", body.path("LockDomainList").asText());
    }

    @Test
    void missing_credentials_throws() {
        // No env vars set in the test environment is the default; passing null
        // apiKey/apiPassword must error.
        JsoProtectorClient client = new JsoProtectorClient();
        var ex = assertThrows(JsoProtectorException.class, () -> client.protect(ProtectOptions.builder()
                .files(Map.of("a.js", "x"))
                .build()));
        assertTrue(ex.getMessage().toLowerCase().contains("credentials"));
    }

    @Test
    void unknown_preset_throws() {
        JsoProtectorClient client = new JsoProtectorClient();
        var ex = assertThrows(JsoProtectorException.class, () -> client.protect(ProtectOptions.builder()
                .apiKey("k").apiPassword("p")
                .files(Map.of("a.js", "x"))
                .preset("elephant")
                .build()));
        assertTrue(ex.getMessage().toLowerCase().contains("preset"));
        assertTrue(ex.getMessage().contains("elephant"));
    }

    @Test
    void empty_files_throws() {
        JsoProtectorClient client = new JsoProtectorClient();
        var ex = assertThrows(JsoProtectorException.class, () -> client.protect(ProtectOptions.builder()
                .apiKey("k").apiPassword("p")
                .files(Map.of())
                .build()));
        assertTrue(ex.getMessage().toLowerCase().contains("file"));
    }

    @Test
    void non_succeed_type_throws_with_metadata() {
        StubHttpClient stub = new StubHttpClient(
                "{\"Type\":\"Error\",\"Message\":\"Invalid API key\",\"ErrorCode\":\"AUTH_FAIL\"}", 200);
        JsoProtectorClient client = new JsoProtectorClient(stub);
        var ex = assertThrows(JsoProtectorException.class, () -> client.protect(ProtectOptions.builder()
                .apiKey("k").apiPassword("p")
                .files(Map.of("a.js", "x"))
                .build()));
        assertEquals("Invalid API key", ex.getMessage());
        assertEquals("Error", ex.type());
        assertEquals("AUTH_FAIL", ex.errorCode());
    }

    @Test
    void preset_table_has_expected_entries() {
        assertTrue(JsoProtectorClient.PRESETS.containsKey("standard"));
        assertTrue(JsoProtectorClient.PRESETS.containsKey("balanced"));
        assertTrue(JsoProtectorClient.PRESETS.containsKey("maximum"));
        assertTrue(JsoProtectorClient.PRESETS.get("maximum").size()
                > JsoProtectorClient.PRESETS.get("standard").size());
    }
}
