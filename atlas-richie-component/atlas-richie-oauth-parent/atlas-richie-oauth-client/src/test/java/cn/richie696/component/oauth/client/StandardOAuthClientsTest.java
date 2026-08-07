package cn.richie696.component.oauth.client;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthTokenRequest;
import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;
import cn.richie696.component.oauth.oidc.OidcProviderMetadata;
import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
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
    void tokenClientSupportsClientSecretPostAndFormEncoding() {
        StubHttpClient http = new StubHttpClient(200, "{\"access_token\":\"at-1\",\"token_type\":\"Bearer\"}");
        StandardOAuthTokenClient client = new StandardOAuthTokenClient(
                URI.create("https://issuer.example/token"), null, "client/1", "secret value",
                Duration.ofSeconds(2), "client_secret_post", http);

        client.requestToken(new OAuthTokenRequest(
                "client_credentials", null, null, null, null, null, null, "read write", null));

        assertThat(http.requestBody).contains("client_id=client%2F1", "client_secret=secret+value")
                .contains("scope=read+write");
        assertThat(http.request.headers().firstValue("Authorization")).isEmpty();
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
    void tokenClientRejectsMissingEndpointAndWrapsTransportFailure() {
        StandardOAuthTokenClient missingEndpoint = new StandardOAuthTokenClient(
                null, null, null, null, Duration.ofSeconds(2), new StubHttpClient(200, "{}"));
        assertThatThrownBy(() -> missingEndpoint.requestToken(new OAuthTokenRequest(
                "client_credentials", null, null, null, null, null, null, null, null)))
                .isInstanceOf(OAuthClientException.class)
                .hasMessageContaining("endpoint 未配置")
                .extracting(exception -> ((OAuthClientException) exception).statusCode())
                .isEqualTo(0);

        StubHttpClient failingHttp = new StubHttpClient(0, "", new IllegalStateException("network down"));
        StandardOAuthTokenClient client = new StandardOAuthTokenClient(
                URI.create("https://issuer.example/token"), null, "client-1", "super-secret",
                Duration.ofSeconds(2), failingHttp);
        assertThatThrownBy(() -> client.requestToken(new OAuthTokenRequest(
                "client_credentials", null, null, null, null, null, null, null, null)))
                .isInstanceOf(OAuthClientException.class)
                .hasMessage("调用 OAuth endpoint 失败")
                .hasRootCauseMessage("network down")
                .hasMessageNotContaining("super-secret");
    }

    @Test
    void tokenClientWrapsTimeoutAndMalformedJson() {
        StubHttpClient timeoutHttp = new StubHttpClient(0, "",
                new HttpTimeoutException("request timed out"));
        StandardOAuthTokenClient timeoutClient = new StandardOAuthTokenClient(
                URI.create("https://issuer.example/token"), null, null, null,
                Duration.ofSeconds(2), timeoutHttp);
        assertThatThrownBy(() -> timeoutClient.requestToken(new OAuthTokenRequest(
                "client_credentials", null, null, null, null, null, null, null, null)))
                .isInstanceOf(OAuthClientException.class)
                .hasMessage("调用 OAuth endpoint 失败")
                .hasCauseInstanceOf(HttpTimeoutException.class);

        StubHttpClient malformedJsonHttp = new StubHttpClient(200, "{not-json");
        StandardOAuthTokenClient malformedJsonClient = new StandardOAuthTokenClient(
                URI.create("https://issuer.example/token"), null, null, null,
                Duration.ofSeconds(2), malformedJsonHttp);
        assertThatThrownBy(() -> malformedJsonClient.requestToken(new OAuthTokenRequest(
                "client_credentials", null, null, null, null, null, null, null, null)))
                .isInstanceOf(OAuthClientException.class)
                .hasMessage("调用 OAuth endpoint 失败");
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
    void oidcUserInfoSurfacesHttpErrorAndDoesNotLeakToken() {
        StubHttpClient http = new StubHttpClient(401, "{\"error\":\"invalid_token\"}");
        StandardOidcUserInfoClient client = new StandardOidcUserInfoClient(
                URI.create("https://issuer.example/userinfo"), http);

        assertThatThrownBy(() -> client.load("very-secret-token"))
                .isInstanceOf(OAuthClientException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining("very-secret-token")
                .extracting(exception -> ((OAuthClientException) exception).statusCode())
                .isEqualTo(401);
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

    @Test
    void metadataResolversExposeHttpFailuresAndOptionalCapabilities() {
        StubHttpClient oauthFailure = new StubHttpClient(503, "{}");
        assertThatThrownBy(() -> new AuthorizationServerMetadataResolver(oauthFailure)
                .resolve(URI.create("https://issuer.example/.well-known/oauth-authorization-server")))
                .isInstanceOf(OAuthClientException.class)
                .extracting(exception -> ((OAuthClientException) exception).statusCode())
                .isEqualTo(503);

        StubHttpClient oidcHttp = new StubHttpClient(200,
                "{\"issuer\":\"https://issuer\",\"frontchannel_logout_supported\":true,"
                        + "\"backchannel_logout_supported\":true,"
                        + "\"response_modes_supported\":[\"query\",\"form_post\"]}");
        OidcProviderMetadata metadata = new OidcProviderMetadataResolver(oidcHttp)
                .resolve(URI.create("https://issuer.example/.well-known/openid-configuration"));
        assertThat(metadata.frontchannelLogoutSupported()).isTrue();
        assertThat(metadata.backchannelLogoutSupported()).isTrue();
        assertThat(metadata.responseModesSupported()).containsExactly("query", "form_post");
    }

    @Test
    void oauthClientExceptionExposesStructuredFailureDetails() {
        OAuthClientException exception = new OAuthClientException(
                "invalid request", 400, "invalid_grant");
        assertThat(exception.statusCode()).isEqualTo(400);
        assertThat(exception.errorCode()).isEqualTo("invalid_grant");
        assertThat(exception.getCause()).isNull();
    }

    private static final class StubHttpClient extends HttpClient {
        private final int status;
        private final String body;
        private final Exception failure;
        private HttpRequest request;
        private String requestBody;

        private StubHttpClient(int status, String body) {
            this(status, body, null);
        }

        private StubHttpClient(int status, String body, Exception failure) {
            this.status = status;
            this.body = body;
            this.failure = failure;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws java.io.IOException, InterruptedException {
            if (failure != null) {
                if (failure instanceof java.io.IOException ioException) {
                    throw ioException;
                }
                if (failure instanceof InterruptedException interruptedException) {
                    throw interruptedException;
                }
                throw new IllegalStateException(failure);
            }
            this.request = request;
            this.requestBody = readBody(request);
            T value = (T) body;
            return new StubHttpResponse<>(status, request, value);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                 HttpResponse.BodyHandler<T> handler) {
            try {
                return CompletableFuture.completedFuture(send(request, handler));
            } catch (Exception exception) {
                CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
                future.completeExceptionally(exception);
                return future;
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                 HttpResponse.BodyHandler<T> handler,
                                                                 HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            try {
                return CompletableFuture.completedFuture(send(request, handler));
            } catch (Exception exception) {
                CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
                future.completeExceptionally(exception);
                return future;
            }
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
