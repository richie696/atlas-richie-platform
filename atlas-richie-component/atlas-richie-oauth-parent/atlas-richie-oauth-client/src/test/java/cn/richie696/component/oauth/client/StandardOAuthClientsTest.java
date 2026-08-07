package cn.richie696.component.oauth.client;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthTokenRequest;
import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;
import cn.richie696.component.oauth.oidc.OidcProviderMetadata;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandardOAuthClientsTest {

    @Test
    void tokenClientSendsFormAndParsesStandardResponse() {
        StubHttpClient http = new StubHttpClient(200,
                "{\"access_token\":\"at-1\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":3600,\"scope\":\"read\"}");
        StandardOAuthTokenClient client = new StandardOAuthTokenClient(
                URI.create("https://issuer.example/token"), null, "client-1", "secret",
                Duration.ofSeconds(2), http);

        OAuthTokenResponse response = client.requestToken(new OAuthTokenRequest(
                "client_credentials", null, null, null, null, null, null,
                "read", "https://api.example"));

        assertThat(response.accessToken()).isEqualTo("at-1");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(http.requestBody).contains("grant_type=client_credentials")
                .contains("resource=https%3A%2F%2Fapi.example");
        assertThat(http.request.headers().firstValue("Authorization")).isPresent();
    }

    @Test
    void tokenClientSurfacesOAuthError() {
        StubHttpClient http = new StubHttpClient(400,
                "{\"error\":\"invalid_grant\",\"error_description\":\"bad code\"}");
        StandardOAuthTokenClient client = new StandardOAuthTokenClient(
                URI.create("https://issuer.example/token"), null, null, null,
                Duration.ofSeconds(2), http);

        assertThatThrownBy(() -> client.requestToken(new OAuthTokenRequest(
                "authorization_code", null, null, "bad", null, null, null, null, null)))
                .isInstanceOf(OAuthClientException.class)
                .hasMessageContaining("400");
    }

    @Test
    void introspectionAndOidcUserInfoUseBearerContract() {
        StubHttpClient introspectionHttp = new StubHttpClient(200,
                "{\"active\":true,\"client_id\":\"client-1\",\"sub\":\"user-1\","
                        + "\"scope\":\"read\",\"iss\":\"issuer\"}");
        OAuthIntrospectionResponse introspection = new StandardOAuthTokenClient(
                null, URI.create("https://issuer.example/introspect"), null, null,
                Duration.ofSeconds(2), introspectionHttp).introspect("at-1");
        assertThat(introspection.active()).isTrue();
        assertThat(introspection.subject()).isEqualTo("user-1");

        StubHttpClient userInfoHttp = new StubHttpClient(200,
                "{\"sub\":\"user-1\",\"name\":\"Richie\"}");
        Map<String, Object> userInfo = new StandardOidcUserInfoClient(
                URI.create("https://issuer.example/userinfo"), userInfoHttp).load("at-1");
        assertThat(userInfo).containsEntry("sub", "user-1");
        assertThat(userInfoHttp.request.headers().firstValue("Authorization"))
                .contains("Bearer at-1");
    }

    @Test
    void metadataResolversParseEndpoints() {
        StubHttpClient oauthHttp = new StubHttpClient(200,
                "{\"issuer\":\"https://issuer\",\"token_endpoint\":\"https://issuer/token\","
                        + "\"grant_types_supported\":[\"client_credentials\"]}");
        assertThat(new AuthorizationServerMetadataResolver(oauthHttp)
                .resolve(URI.create("https://issuer.example/.well-known/oauth-authorization-server"))
                .issuer()).isEqualTo("https://issuer");

        StubHttpClient oidcHttp = new StubHttpClient(200,
                "{\"issuer\":\"https://issuer\",\"userinfo_endpoint\":\"https://issuer/userinfo\","
                        + "\"response_types_supported\":[\"code\"]}");
        OidcProviderMetadata metadata = new OidcProviderMetadataResolver(oidcHttp)
                .resolve(URI.create("https://issuer.example/.well-known/openid-configuration"));
        assertThat(metadata.userInfoEndpoint()).isEqualTo("https://issuer/userinfo");
    }

    private static final class StubHttpClient extends HttpClient {
        private final int status;
        private final String body;
        private HttpRequest request;
        private String requestBody;

        private StubHttpClient(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            this.request = request;
            this.requestBody = readBody(request);
            T value = (T) body;
            return new StubHttpResponse<>(status, request, value);
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
            try {
                return SSLContext.getDefault();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }

        private String readBody(HttpRequest request) {
            if (request.bodyPublisher().isEmpty()) return "";
            StringBuilder result = new StringBuilder();
            CountDownLatch complete = new CountDownLatch(1);
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                @Override public void onNext(ByteBuffer item) {
                    ByteBuffer copy = item.slice();
                    byte[] bytes = new byte[copy.remaining()];
                    copy.get(bytes);
                    result.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                }
                @Override public void onError(Throwable throwable) { complete.countDown(); }
                @Override public void onComplete() { complete.countDown(); }
            });
            try {
                complete.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return result.toString();
        }
    }

    private record StubHttpResponse<T>(int statusCode, HttpRequest request, T body)
            implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.<String, List<String>>of(), (a, b) -> true); }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
