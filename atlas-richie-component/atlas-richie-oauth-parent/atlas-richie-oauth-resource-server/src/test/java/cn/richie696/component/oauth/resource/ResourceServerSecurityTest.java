package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceServerSecurityTest {

    @Test
    void cachingIntrospectionCachesByHashAndRejectsBlankToken() {
        InMemoryOAuthCache cache = new InMemoryOAuthCache();
        int[] calls = {0};
        OAuthIntrospectionResponse active = new OAuthIntrospectionResponse(
                true, "client-1", "Bearer", "read", "user-1", "issuer",
                "api", 100, 90, "jti-1", Map.of());
        IntrospectionClient delegate = token -> {
            calls[0]++;
            return active;
        };
        CachingIntrospectionClient client = new CachingIntrospectionClient(cache, delegate, 10_000);

        assertThat(client.introspect("token")).isEqualTo(active);
        assertThat(client.introspect("token")).isEqualTo(active);
        assertThat(calls[0]).isEqualTo(1);
        assertThatThrownBy(() -> client.introspect(" "))
                .isInstanceOf(ResourceServerException.class);
    }

    @Test
    void authenticatorFallsBackOnlyToActiveIntrospectionResult() {
        JwtTokenVerifier verifier = token -> {
            throw new ResourceServerException("bad signature");
        };
        IntrospectionClient introspection = token -> "token".equals(token)
                ? new OAuthIntrospectionResponse(true, "client-1", "Bearer", "read", "user-1",
                "issuer", "api", 100, 90, "jti-1", Map.of("tenant_id", "tenant-1"))
                : OAuthIntrospectionResponse.inactive();

        OAuthPrincipal principal = new ResourceServerAuthenticator(verifier, introspection, true)
                .authenticate("token");
        assertThat(principal.subject()).isEqualTo("user-1");
        assertThat(principal.claims()).containsEntry("tenant_id", "tenant-1");

        assertThatThrownBy(() -> new ResourceServerAuthenticator(verifier, introspection, true)
                .authenticate("inactive"))
                .isInstanceOf(ResourceServerException.class);
    }

    @Test
    void httpJwkSourceLoadsAndRefreshesRotatedKey() throws Exception {
        KeyPair first = rsa();
        KeyPair second = rsa();
        SequenceHttpClient http = new SequenceHttpClient(
                jwks("k1", (RSAPublicKey) first.getPublic()),
                jwks("k2", (RSAPublicKey) second.getPublic()));
        HttpJwkSource source = new HttpJwkSource(
                URI.create("https://issuer.example/jwks"), new InMemoryOAuthCache(),
                Duration.ofSeconds(2), Duration.ofMinutes(1), http);

        assertThat(source.find("k1")).isEqualTo(first.getPublic());
        assertThat(source.find("k2")).isEqualTo(second.getPublic());
        assertThat(http.calls).isEqualTo(2);
    }

    @Test
    void metricsAvoidSensitiveHighCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OAuthResourceServerMetrics metrics = new OAuthResourceServerMetrics(registry);
        ResourceServerAuthenticator authenticator = new ResourceServerAuthenticator(
                token -> new OAuthPrincipal("user", "client", "issuer", "api", "jti", List.of(), Map.of()),
                null, false, metrics);

        authenticator.authenticate("token");

        assertThat(registry.counter("oauth.resource.authentication", "result", "success").count())
                .isEqualTo(1);
        assertThat(registry.getMeters()).allMatch(meter -> meter.getId().getTags().stream()
                .noneMatch(tag -> tag.getKey().equals("token") || tag.getKey().equals("subject")));
    }

    private KeyPair rsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String jwks(String kid, RSAPublicKey key) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid
                + "\",\"n\":\"" + base64(key.getModulus())
                + "\",\"e\":\"" + base64(key.getPublicExponent()) + "\"}]}";
    }

    private String base64(BigInteger value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray());
    }

    private static final class SequenceHttpClient extends HttpClient {
        private final String[] bodies;
        private int calls;

        private SequenceHttpClient(String... bodies) {
            this.bodies = bodies;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            String body = bodies[Math.min(calls++, bodies.length - 1)];
            return new StubResponse<>(200, request, (T) body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                 HttpResponse.BodyHandler<T> handler) {
            return CompletableFuture.completedFuture(send(request, handler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                 HttpResponse.BodyHandler<T> handler,
                                                                 HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.completedFuture(send(request, handler));
        }

        @Override public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(2)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() {
            try { return SSLContext.getDefault(); } catch (Exception e) { throw new IllegalStateException(e); }
        }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
    }

    private record StubResponse<T>(int statusCode, HttpRequest request, T body)
            implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.<String, List<String>>of(), (a, b) -> true); }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
