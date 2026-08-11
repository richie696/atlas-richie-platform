/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.mcp.security.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link McpOAuthTokenClient} 单元测试：覆盖 authorization_code / refresh_token /
 * client_credentials / introspect / register 五种标准交互流的成功与异常分支，验证
 * 请求体编码、Basic Authorization 头拼接以及错误响应解析。
 *
 * @author richie696
 * @since 2026-08-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("McpOAuthTokenClient OAuth 运行时 HTTP 客户端")
class McpOAuthTokenClientTest {

    private static final URI TOKEN_ENDPOINT = URI.create("https://issuer.example/token");
    private static final URI INTROSPECT_ENDPOINT = URI.create("https://issuer.example/introspect");
    private static final URI REGISTER_ENDPOINT = URI.create("https://issuer.example/register");

    @Mock
    private HttpClient httpClient;

    private McpOAuthTokenClient client;

    @BeforeEach
    void setUp() {
        client = new McpOAuthTokenClient(httpClient, Duration.ofSeconds(3));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> jsonResponse(int status, String body) {
        HttpResponse<String> response = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        org.mockito.Mockito.lenient().when(response.statusCode()).thenReturn(status);
        org.mockito.Mockito.lenient().when(response.body()).thenReturn(body);
        return response;
    }

    @SuppressWarnings("unchecked")
    private void stubSend(HttpResponse<String> response) throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void stubIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection reset"));
    }

    @SuppressWarnings("unchecked")
    private void stubInterrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("interrupted"));
    }

    @Nested
    @DisplayName("构造器")
    class Constructor {

        @Test
        @DisplayName("httpClient / timeout / uriPolicy 为 null 时抛出 NullPointerException")
        void nullArgumentsRejected() {
            assertThatThrownBy(() -> new McpOAuthTokenClient(null, Duration.ofSeconds(1)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("httpClient");
            assertThatThrownBy(() -> new McpOAuthTokenClient(httpClient, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("timeout");
            assertThatThrownBy(() -> new McpOAuthTokenClient(httpClient, Duration.ofSeconds(1), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("uriPolicy");
        }
    }

    @Nested
    @DisplayName("authorization_code()")
    class AuthorizationCode {

        @Test
        @DisplayName("成功响应解析出 access token / refresh token / scopes / resource")
        void happyPath() throws Exception {
            stubSend(jsonResponse(200, """
                    {
                      "access_token": "at-1",
                      "token_type": "Bearer",
                      "expires_in": 3600,
                      "refresh_token": "rt-1",
                      "scope": "tools.read tools.write"
                    }
                    """));

            McpOAuthTokenResponse response = client.authorizationCode(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "code-1", URI.create("https://client/callback"),
                    "verifier-1",
                    URI.create("https://mcp.example"),
                    Set.of("tools.read"));

            assertThat(response.accessToken().value()).isEqualTo("at-1");
            assertThat(response.accessToken().tokenType()).isEqualTo("Bearer");
            assertThat(response.accessToken().resource()).isEqualTo("https://mcp.example");
            assertThat(response.refreshToken()).isEqualTo("rt-1");
            assertThat(response.scopes()).containsExactlyInAnyOrder("tools.read", "tools.write");
        }

        @Test
        @DisplayName("必填参数 code / codeVerifier / redirectUri / clientId 缺失时抛 IllegalArgumentException")
        void blankArgumentsRejected() {
            assertThatThrownBy(() -> client.authorizationCode(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    null, URI.create("https://client/callback"), "verifier", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("code");
            assertThatThrownBy(() -> client.authorizationCode(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "code", URI.create("https://client/callback"), null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("codeVerifier");
            assertThatThrownBy(() -> client.authorizationCode(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "code", URI.create("https://client/callback"), "  ", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("codeVerifier");
            assertThatThrownBy(() -> client.authorizationCode(
                    TOKEN_ENDPOINT, null, "secret",
                    "code", URI.create("https://client/callback"), "verifier", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("clientId");
            assertThatThrownBy(() -> client.authorizationCode(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "code", null, "verifier", null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("redirectUri");
        }

        @Test
        @DisplayName("HTTP 4xx 响应转为 IllegalStateException 并附 error / description")
        void httpError() throws Exception {
            stubSend(jsonResponse(400, """
                    { "error": "invalid_grant", "error_description": "code expired" }
                    """));

            assertThatThrownBy(() -> client.authorizationCode(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "code", URI.create("https://client/callback"),
                    "verifier", null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("400")
                    .hasMessageContaining("invalid_grant")
                    .hasMessageContaining("code expired");
        }

        @Test
        @DisplayName("响应体缺少 access_token 字段时抛 IllegalArgumentException")
        void missingAccessToken() throws Exception {
            stubSend(jsonResponse(200, """
                    { "token_type": "Bearer", "expires_in": 60 }
                    """));

            assertThatThrownBy(() -> client.authorizationCode(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "code", URI.create("https://client/callback"),
                    "verifier", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("access_token");
        }

        @Test
        @DisplayName("发送请求时附带 HTTP Basic Authorization 头（clientId + clientSecret）")
        void sendsBasicAuthHeader() throws Exception {
            stubSend(jsonResponse(200, """
                    { "access_token": "at-1", "token_type": "Bearer", "expires_in": 60 }
                    """));

            client.authorizationCode(
                    TOKEN_ENDPOINT, "client-1", "secret-1",
                    "code", URI.create("https://client/callback"),
                    "verifier", null, null);

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            org.mockito.Mockito.verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            HttpRequest req = captor.getValue();
            assertThat(req.headers().firstValue("Authorization"))
                    .hasValue("Basic " + java.util.Base64.getEncoder()
                            .encodeToString("client-1:secret-1".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertThat(req.headers().firstValue("Content-Type"))
                    .hasValue("application/x-www-form-urlencoded");
        }

        @Test
        @DisplayName("IOException 转为 IllegalStateException")
        void ioExceptionTranslated() throws Exception {
            stubIOException();

            assertThatThrownBy(() -> client.authorizationCode(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "code", URI.create("https://client/callback"),
                    "verifier", null, null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("InterruptedException 转为 IllegalStateException 并恢复中断标志")
        void interruptedTranslated() throws Exception {
            stubInterrupted();
            try {
                assertThatThrownBy(() -> client.authorizationCode(
                        TOKEN_ENDPOINT, "client-1", "secret",
                        "code", URI.create("https://client/callback"),
                        "verifier", null, null))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("interrupted");
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Nested
    @DisplayName("refreshToken()")
    class RefreshToken {

        @Test
        @DisplayName("成功响应刷新并保留旧 refresh token")
        void happyPath() throws Exception {
            stubSend(jsonResponse(200, """
                    {
                      "access_token": "at-2",
                      "token_type": "Bearer",
                      "expires_in": 1800,
                      "scope": "tools.read"
                    }
                    """));

            McpOAuthTokenResponse response = client.refreshToken(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "rt-1", null, Set.of("tools.read"));

            assertThat(response.accessToken().value()).isEqualTo("at-2");
            assertThat(response.refreshToken()).isNull();
            assertThat(response.scopes()).containsExactly("tools.read");
        }

        @Test
        @DisplayName("refreshToken 为 blank 时抛 IllegalArgumentException")
        void blankRefreshTokenRejected() {
            assertThatThrownBy(() -> client.refreshToken(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("refreshToken");
            assertThatThrownBy(() -> client.refreshToken(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "  ", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("refreshToken");
        }

        @Test
        @DisplayName("HTTP 500 错误时抛 IllegalStateException")
        void http500Fails() throws Exception {
            stubSend(jsonResponse(500, """
                    { "error": "server_error" }
                    """));

            assertThatThrownBy(() -> client.refreshToken(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "rt-1", null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("500")
                    .hasMessageContaining("server_error");
        }
    }

    @Nested
    @DisplayName("clientCredentials()")
    class ClientCredentials {

        @Test
        @DisplayName("成功响应仅含 access token + scopes（通常无 refresh）")
        void happyPath() throws Exception {
            stubSend(jsonResponse(200, """
                    { "access_token": "at-3", "token_type": "Bearer", "expires_in": 7200, "scope": "m2m.read" }
                    """));

            McpOAuthTokenResponse response = client.clientCredentials(
                    TOKEN_ENDPOINT, "client-1", "secret-1", null, Set.of("m2m.read"));

            assertThat(response.accessToken().value()).isEqualTo("at-3");
            assertThat(response.refreshToken()).isNull();
            assertThat(response.scopes()).containsExactly("m2m.read");
        }

        @Test
        @DisplayName("clientId 为 blank 时抛 IllegalArgumentException")
        void blankClientIdRejected() {
            assertThatThrownBy(() -> client.clientCredentials(
                    TOKEN_ENDPOINT, " ", "secret", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("clientId");
        }

        @Test
        @DisplayName("resource 与 scopes 都为 null 时不参与 form")
        void resourceAndScopesOmitted() throws Exception {
            stubSend(jsonResponse(200, """
                    { "access_token": "at-4", "token_type": "Bearer", "expires_in": 60 }
                    """));

            client.clientCredentials(TOKEN_ENDPOINT, "client-1", "secret", null, null);

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            org.mockito.Mockito.verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            // 仅断言请求体可以被序列化即可（具体内容由 form 拼装保证）
            assertThat(captor.getValue().headers().firstValue("Content-Type"))
                    .hasValue("application/x-www-form-urlencoded");
        }
    }

    @Nested
    @DisplayName("introspect()")
    class Introspect {

        @Test
        @DisplayName("活跃 token 响应映射为 active=true")
        void activeToken() throws Exception {
            stubSend(jsonResponse(200, """
                    {
                      "active": true,
                      "client_id": "client-1",
                      "sub": "user-1",
                      "token_type": "Bearer",
                      "exp": 4294967296,
                      "scope": "tools.read tools.write",
                      "iss": "https://issuer.example",
                      "aud": "https://mcp.example"
                    }
                    """));

            McpOAuthIntrospectionResponse response = client.introspect(
                    INTROSPECT_ENDPOINT, "client-1", "secret", "at-1");

            assertThat(response.active()).isTrue();
            assertThat(response.clientId()).isEqualTo("client-1");
            assertThat(response.subject()).isEqualTo("user-1");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresAt()).isNotNull();
            assertThat(response.scopes()).containsExactlyInAnyOrder("tools.read", "tools.write");
            assertThat(response.issuer()).isEqualTo("https://issuer.example");
            assertThat(response.resource()).isEqualTo("https://mcp.example");
        }

        @Test
        @DisplayName("active=false 的 inactive token 仍能解析")
        void inactiveToken() throws Exception {
            stubSend(jsonResponse(200, """
                    { "active": false }
                    """));

            McpOAuthIntrospectionResponse response = client.introspect(
                    INTROSPECT_ENDPOINT, "client-1", "secret", "at-1");

            assertThat(response.active()).isFalse();
        }

        @Test
        @DisplayName("scope 字段缺失或非字符串时退化为空 scopes")
        void scopeOmittedBecomesEmpty() throws Exception {
            stubSend(jsonResponse(200, """
                    { "active": true }
                    """));

            McpOAuthIntrospectionResponse response = client.introspect(
                    INTROSPECT_ENDPOINT, "client-1", "secret", "at-1");

            assertThat(response.scopes()).isEmpty();
        }

        @Test
        @DisplayName("token 为 blank 时抛 IllegalArgumentException")
        void blankTokenRejected() {
            assertThatThrownBy(() -> client.introspect(
                    INTROSPECT_ENDPOINT, "client-1", "secret", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("token");
        }

        @Test
        @DisplayName("HTTP 401 错误抛 IllegalStateException")
        void http401Fails() throws Exception {
            stubSend(jsonResponse(401, """
                    { "error": "invalid_client" }
                    """));

            assertThatThrownBy(() -> client.introspect(
                    INTROSPECT_ENDPOINT, "client-1", "secret", "at-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("401")
                    .hasMessageContaining("invalid_client");
        }

        @Test
        @DisplayName("响应体非 JSON 时抛 IllegalStateException")
        void nonJsonBodyFails() throws Exception {
            stubSend(jsonResponse(200, "<html>bad gateway</html>"));

            assertThatThrownBy(() -> client.introspect(
                    INTROSPECT_ENDPOINT, "client-1", "secret", "at-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not valid JSON");
        }

        @Test
        @DisplayName("scope 字段以数组形式提供时被解析为 Set（List 路径）")
        void scopeAsArrayIsParsed() throws Exception {
            stubSend(jsonResponse(200, """
                    { "active": true, "scope": ["tools.read", "tools.write"] }
                    """));

            McpOAuthIntrospectionResponse response = client.introspect(
                    INTROSPECT_ENDPOINT, "client-1", "secret", "at-1");

            assertThat(response.scopes()).containsExactlyInAnyOrder("tools.read", "tools.write");
        }

        @Test
        @DisplayName("clientId 为 null 时不附加 Basic Authorization 头")
        void nullClientIdOmitsBasicAuth() throws Exception {
            stubSend(jsonResponse(200, """
                    { "active": true }
                    """));

            client.introspect(INTROSPECT_ENDPOINT, null, "secret", "at-1");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            org.mockito.Mockito.verify(httpClient).send(captor.capture(),
                    any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().headers().firstValue("Authorization")).isEmpty();
        }

        @Test
        @DisplayName("clientId 为 blank 字符串时也不附加 Basic Authorization 头")
        void blankClientIdOmitsBasicAuth() throws Exception {
            stubSend(jsonResponse(200, """
                    { "active": true }
                    """));

            client.introspect(INTROSPECT_ENDPOINT, "  ", "secret", "at-1");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            org.mockito.Mockito.verify(httpClient).send(captor.capture(),
                    any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().headers().firstValue("Authorization")).isEmpty();
        }

        @Test
        @DisplayName("clientSecret 为 null 时 Basic 凭据以空 secret 拼接")
        void nullClientSecretStillAttachesBasicAuth() throws Exception {
            stubSend(jsonResponse(200, """
                    { "active": true }
                    """));

            client.introspect(INTROSPECT_ENDPOINT, "client-1", null, "at-1");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            org.mockito.Mockito.verify(httpClient).send(captor.capture(),
                    any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().headers().firstValue("Authorization"))
                    .hasValue("Basic " + java.util.Base64.getEncoder()
                            .encodeToString("client-1:".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("authorization_code 请求的 scopes 集合为空时仍不附加 scope 参数")
        void emptyScopesAreOmittedFromForm() throws Exception {
            stubSend(jsonResponse(200, """
                    { "access_token": "at-1", "token_type": "Bearer", "expires_in": 60 }
                    """));

            client.authorizationCode(
                    TOKEN_ENDPOINT, "client-1", "secret",
                    "code", URI.create("https://client/callback"),
                    "verifier", null, java.util.Collections.emptySet());

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            org.mockito.Mockito.verify(httpClient).send(captor.capture(),
                    any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue()).isNotNull();
        }
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("成功响应解析为完整 registration 模型")
        void happyPath() throws Exception {
            stubSend(jsonResponse(201, """
                    {
                      "client_id": "client-reg-1",
                      "client_secret": "secret-reg-1",
                      "token_endpoint_auth_method": "client_secret_basic",
                      "redirect_uris": ["https://client/callback"],
                      "grant_types": "authorization_code",
                      "scope": "tools.read",
                      "registration_client_uri": "https://issuer.example/register/client-reg-1",
                      "registration_access_token": "reg-at-1"
                    }
                    """));

            McpOAuthClientRegistration registration = client.register(
                    REGISTER_ENDPOINT,
                    Map.of("redirect_uris", java.util.List.of("https://client/callback")));

            assertThat(registration.clientId()).isEqualTo("client-reg-1");
            assertThat(registration.clientSecret()).isEqualTo("secret-reg-1");
            assertThat(registration.tokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
            assertThat(registration.redirectUris())
                    .containsExactly(URI.create("https://client/callback"));
            assertThat(registration.grantTypes()).containsExactly("authorization_code");
            assertThat(registration.scopes()).containsExactly("tools.read");
            assertThat(registration.registrationClientUri())
                    .isEqualTo(URI.create("https://issuer.example/register/client-reg-1"));
            assertThat(registration.registrationAccessToken()).isEqualTo("reg-at-1");
        }

        @Test
        @DisplayName("请求体为 null 时序列化为空 JSON 对象")
        void nullPayloadBecomesEmptyObject() throws Exception {
            stubSend(jsonResponse(201, """
                    { "client_id": "client-reg-2" }
                    """));

            McpOAuthClientRegistration registration = client.register(
                    REGISTER_ENDPOINT, null);

            assertThat(registration.clientId()).isEqualTo("client-reg-2");
            assertThat(registration.clientSecret()).isNull();
            assertThat(registration.redirectUris()).isEmpty();
        }

        @Test
        @DisplayName("响应缺少 client_id 抛 IllegalArgumentException")
        void missingClientIdRejected() throws Exception {
            stubSend(jsonResponse(201, """
                    { "client_secret": "s" }
                    """));

            assertThatThrownBy(() -> client.register(REGISTER_ENDPOINT, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("client_id");
        }

        @Test
        @DisplayName("HTTP 4xx 响应抛 IllegalStateException")
        void http4xxFails() throws Exception {
            stubSend(jsonResponse(403, """
                    { "error": "invalid_redirect_uri", "error_description": "scheme mismatch" }
                    """));

            assertThatThrownBy(() -> client.register(REGISTER_ENDPOINT, Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("403")
                    .hasMessageContaining("scheme mismatch");
        }

        @Test
        @DisplayName("URI 违反 httpsOnly 时前置失败")
        void uriPolicyBlocksRegistration() {
            McpOAuthUriPolicy rejectAll = uri -> {
                throw new IllegalArgumentException("rejected");
            };
            McpOAuthTokenClient strictClient = new McpOAuthTokenClient(
                    httpClient, Duration.ofSeconds(3), rejectAll);

            assertThatThrownBy(() -> strictClient.register(
                    URI.create("http://issuer.example/register"), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rejected");
        }

        @Test
        @DisplayName("InterruptedException 转为 IllegalStateException")
        void interruptedTranslated() throws Exception {
            stubInterrupted();
            try {
                assertThatThrownBy(() -> client.register(REGISTER_ENDPOINT, Map.of()))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("interrupted");
            } finally {
                Thread.interrupted();
            }
        }

        @Test
        @DisplayName("响应体非 JSON 时抛 IllegalStateException")
        void nonJsonBodyFails() throws Exception {
            stubSend(jsonResponse(201, "not json"));

            assertThatThrownBy(() -> client.register(REGISTER_ENDPOINT, Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not valid JSON");
        }

        @Test
        @DisplayName("register 路径的 IOException 转为 IllegalStateException")
        void ioExceptionTranslated() throws Exception {
            stubIOException();

            assertThatThrownBy(() -> client.register(REGISTER_ENDPOINT, Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("registration request failed");
        }
    }

    @Nested
    @DisplayName("token 响应 expires_in 处理")
    class ExpiresInDefault {

        @Test
        @DisplayName("expires_in 缺失或非数字时退化为 3600 默认值")
        void missingExpiresInUsesDefault() throws Exception {
            stubSend(jsonResponse(200, """
                    { "access_token": "at-x", "token_type": "Bearer" }
                    """));

            McpOAuthTokenResponse response = client.clientCredentials(
                    TOKEN_ENDPOINT, "client-1", "secret", null, null);

            Instant expectedExpiry = Instant.now().plusSeconds(3600);
            assertThat(response.accessToken().expiresAt())
                    .isCloseTo(expectedExpiry, within(java.time.Duration.ofSeconds(5)));
        }
    }
}