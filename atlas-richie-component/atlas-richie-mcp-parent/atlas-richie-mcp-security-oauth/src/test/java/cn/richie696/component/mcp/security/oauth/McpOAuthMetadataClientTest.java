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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link McpOAuthMetadataClient} 单元测试：使用 Mockito 桩化 JDK {@link HttpClient}，
 * 覆盖 PRM/AS metadata 的成功解析、HTTP 失败、JSON 解析失败、必填字段缺失与 URI 策略失败
 * 五大分支。
 *
 * @author richie696
 * @since 2026-08-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("McpOAuthMetadataClient metadata 发现客户端")
class McpOAuthMetadataClientTest {

    private static final URI PRM_URI = URI.create("https://issuer.example/.well-known/oauth-protected-resource");
    private static final URI AS_URI = URI.create("https://issuer.example/.well-known/oauth-authorization-server");

    @Mock
    private HttpClient httpClient;

    private McpOAuthMetadataClient client;

    @BeforeEach
    void setUp() {
        client = new McpOAuthMetadataClient(httpClient, Duration.ofSeconds(3));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> stubResponse(int status, String body) throws Exception {
        HttpResponse<String> response = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }

    @SuppressWarnings("unchecked")
    private void stubSend(HttpResponse<String> response) throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void stubIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("network down"));
    }

    @SuppressWarnings("unchecked")
    private void stubInterrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("timeout"));
    }

    @Nested
    @DisplayName("构造器")
    class Constructor {

        @Test
        @DisplayName("httpClient / timeout 为 null 时抛出 NullPointerException")
        void nullArgumentsRejected() {
            assertThatThrownBy(() -> new McpOAuthMetadataClient(null, Duration.ofSeconds(1)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("httpClient");
            assertThatThrownBy(() -> new McpOAuthMetadataClient(httpClient, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("timeout");
        }

        @Test
        @DisplayName("主构造器对 uriPolicy 也做非空校验")
        void nullPolicyRejected() {
            assertThatThrownBy(() -> new McpOAuthMetadataClient(httpClient, Duration.ofSeconds(1), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("uriPolicy");
        }
    }

    @Nested
    @DisplayName("fetchProtectedResourceMetadata()")
    class FetchProtectedResourceMetadata {

        @Test
        @DisplayName("成功解析 RFC 9728 必填/可选字段并透传扩展字段")
        void happyPath() throws Exception {
            stubSend(stubResponse(200, """
                    {
                      "resource": "https://mcp.example/mcp",
                      "authorization_servers": ["https://issuer.example"],
                      "scopes_supported": ["tools.read"],
                      "bearer_methods_supported": ["header"],
                      "resource_documentation": "https://docs.example/mcp"
                    }
                    """));

            McpProtectedResourceMetadata metadata = client.fetchProtectedResourceMetadata(PRM_URI);

            assertThat(metadata.resource()).isEqualTo(URI.create("https://mcp.example/mcp"));
            assertThat(metadata.authorizationServers())
                    .containsExactly(URI.create("https://issuer.example"));
            assertThat(metadata.scopesSupported()).containsExactly("tools.read");
            assertThat(metadata.extensions())
                    .containsEntry("bearer_methods_supported", List.of("header"))
                    .containsEntry("resource_documentation", "https://docs.example/mcp");
        }

        @Test
        @DisplayName("必填字段 resource 缺失时抛出 IllegalArgumentException")
        void missingResourceRejected() throws Exception {
            stubSend(stubResponse(200, """
                    { "authorization_servers": ["https://issuer.example"] }
                    """));

            assertThatThrownBy(() -> client.fetchProtectedResourceMetadata(PRM_URI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("resource");
        }

        @Test
        @DisplayName("authorization_servers 类型错误（非数组）抛出 IllegalArgumentException")
        void authorizationServersWrongType() throws Exception {
            stubSend(stubResponse(200, """
                    { "resource": "https://mcp.example", "authorization_servers": "https://issuer.example" }
                    """));

            assertThatThrownBy(() -> client.fetchProtectedResourceMetadata(PRM_URI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("authorization_servers");
        }

        @Test
        @DisplayName("HTTP 非 2xx 时抛出 IllegalStateException 携带状态码")
        void non2xxTriggersFailure() throws Exception {
            stubSend(stubResponse(503, "service unavailable"));

            assertThatThrownBy(() -> client.fetchProtectedResourceMetadata(PRM_URI))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("503");
        }

        @Test
        @DisplayName("响应体非 JSON 时抛出 IllegalStateException")
        void nonJsonBodyFails() throws Exception {
            stubSend(stubResponse(200, "<html>not json</html>"));

            assertThatThrownBy(() -> client.fetchProtectedResourceMetadata(PRM_URI))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not valid JSON");
        }

        @Test
        @DisplayName("IOException 时抛出 IllegalStateException")
        void ioExceptionTranslated() throws Exception {
            stubIOException();

            assertThatThrownBy(() -> client.fetchProtectedResourceMetadata(PRM_URI))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("InterruptedException 时抛出 IllegalStateException 并恢复中断标志")
        void interruptedTranslatedAndFlagRestored() throws Exception {
            stubInterrupted();

            try {
                assertThatThrownBy(() -> client.fetchProtectedResourceMetadata(PRM_URI))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("interrupted");
            } finally {
                // 子线程兜底，避免影响其他测试
                Thread.interrupted();
            }
        }

        @Test
        @DisplayName("URI 违反 httpsOnly 策略时前置失败")
        void uriPolicyBlocksRequest() {
            McpOAuthMetadataClient strictClient = new McpOAuthMetadataClient(
                    httpClient, Duration.ofSeconds(3));

            assertThatThrownBy(() -> strictClient.fetchProtectedResourceMetadata(
                    URI.create("http://issuer.example/meta")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTPS");
        }
    }

    @Nested
    @DisplayName("fetchAuthorizationServerMetadata()")
    class FetchAuthorizationServerMetadata {

        @Test
        @DisplayName("成功解析 issuer / endpoints / PKCE 列表")
        void happyPath() throws Exception {
            stubSend(stubResponse(200, """
                    {
                      "issuer": "https://issuer.example",
                      "authorization_endpoint": "https://issuer.example/authorize",
                      "token_endpoint": "https://issuer.example/token",
                      "registration_endpoint": "https://issuer.example/register",
                      "response_types_supported": ["code"],
                      "grant_types_supported": ["authorization_code", "refresh_token"],
                      "code_challenge_methods_supported": ["S256"]
                    }
                    """));

            McpAuthorizationServerMetadata metadata = client.fetchAuthorizationServerMetadata(AS_URI);

            assertThat(metadata.issuer()).isEqualTo(URI.create("https://issuer.example"));
            assertThat(metadata.authorizationEndpoint())
                    .isEqualTo(URI.create("https://issuer.example/authorize"));
            assertThat(metadata.tokenEndpoint())
                    .isEqualTo(URI.create("https://issuer.example/token"));
            assertThat(metadata.registrationEndpoint())
                    .isEqualTo(URI.create("https://issuer.example/register"));
            assertThat(metadata.responseTypesSupported()).containsExactly("code");
            assertThat(metadata.grantTypesSupported())
                    .containsExactly("authorization_code", "refresh_token");
            assertThat(metadata.codeChallengeMethodsSupported()).containsExactly("S256");
        }

        @Test
        @DisplayName("必填 issuer 缺失时抛出 IllegalArgumentException")
        void missingIssuerRejected() throws Exception {
            stubSend(stubResponse(200, """
                    { "token_endpoint": "https://issuer.example/token" }
                    """));

            assertThatThrownBy(() -> client.fetchAuthorizationServerMetadata(AS_URI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("issuer");
        }

        @Test
        @DisplayName("scopes_supported 类型错误（非数组）抛出 IllegalArgumentException")
        void stringListWrongType() throws Exception {
            stubSend(stubResponse(200, """
                    { "issuer": "https://issuer.example", "response_types_supported": "code" }
                    """));

            assertThatThrownBy(() -> client.fetchAuthorizationServerMetadata(AS_URI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("response_types_supported");
        }

        @Test
        @DisplayName("scopes_supported 数组含空字符串时被拒绝")
        void stringListContainsBlank() throws Exception {
            stubSend(stubResponse(200, """
                    { "issuer": "https://issuer.example", "response_types_supported": [""] }
                    """));

            assertThatThrownBy(() -> client.fetchAuthorizationServerMetadata(AS_URI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid value");
        }

        @Test
        @DisplayName("endpoints 全部缺失时仅 issuer 必填，其余为空")
        void endpointsMissingIsAllowed() throws Exception {
            stubSend(stubResponse(200, """
                    { "issuer": "https://issuer.example" }
                    """));

            McpAuthorizationServerMetadata metadata = client.fetchAuthorizationServerMetadata(AS_URI);

            assertThat(metadata.authorizationEndpoint()).isNull();
            assertThat(metadata.tokenEndpoint()).isNull();
            assertThat(metadata.registrationEndpoint()).isNull();
        }

        @Test
        @DisplayName("HTTP 4xx 时抛出 IllegalStateException")
        void http4xxFails() throws Exception {
            stubSend(stubResponse(404, "not found"));

            assertThatThrownBy(() -> client.fetchAuthorizationServerMetadata(AS_URI))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("URI 违反策略时前置失败")
        void uriPolicyBlocksAsRequest() {
            assertThatThrownBy(() -> client.fetchAuthorizationServerMetadata(
                    URI.create("https://user:pwd@issuer.example/.well-known/as")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("credentials");
        }

        @Test
        @DisplayName("URI 含 fragment 时前置失败")
        void uriPolicyBlocksFragment() {
            assertThatThrownBy(() -> client.fetchAuthorizationServerMetadata(
                    URI.create("https://issuer.example/.well-known/as#fragment")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("credentials or fragment");
        }

        @Test
        @DisplayName("可注入自定义 URI 策略并被强制执行")
        void customUriPolicyApplied() throws Exception {
            McpOAuthUriPolicy rejectAll = uri -> {
                throw new IllegalArgumentException("rejected by test policy");
            };
            McpOAuthMetadataClient strictClient = new McpOAuthMetadataClient(
                    httpClient, Duration.ofSeconds(3), rejectAll);

            assertThatThrownBy(() -> strictClient.fetchAuthorizationServerMetadata(AS_URI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rejected by test policy");
        }
    }

    @Nested
    @DisplayName("JSON 容错")
    class JsonHandling {

        @Test
        @DisplayName("响应体是空对象时 AS metadata 退化为只含 issuer")
        void emptyObjectAllowed() throws Exception {
            stubSend(stubResponse(200, """
                    { "issuer": "https://issuer.example" }
                    """));

            McpAuthorizationServerMetadata metadata = client.fetchAuthorizationServerMetadata(AS_URI);

            assertThat(metadata.issuer()).isEqualTo(URI.create("https://issuer.example"));
            assertThat(metadata.authorizationEndpoint()).isNull();
            assertThat(metadata.responseTypesSupported()).isEmpty();
        }

        @Test
        @DisplayName("authorization_servers 元素为非 URI 字符串时被拒绝")
        void invalidUriInList() throws Exception {
            stubSend(stubResponse(200, """
                    {
                      "resource": "https://mcp.example",
                      "authorization_servers": ["not-a-valid-uri"]
                    }
                    """));

            assertThatThrownBy(() -> client.fetchProtectedResourceMetadata(PRM_URI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("authorization_servers[]");
        }

        @Test
        @DisplayName("scopes_supported 数组含非字符串元素时被拒绝")
        void stringListContainsNonString() throws Exception {
            stubSend(stubResponse(200, """
                    { "resource": "https://mcp.example", "scopes_supported": [1, "tools.read"] }
                    """));

            assertThatThrownBy(() -> client.fetchProtectedResourceMetadata(PRM_URI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("scopes_supported");
        }

        @Test
        @DisplayName("authorization_endpoint 字段为 blank 字符串时被拒绝")
        void blankEndpointRejected() throws Exception {
            stubSend(stubResponse(200, """
                    { "issuer": "https://issuer.example", "authorization_endpoint": "   " }
                    """));

            assertThatThrownBy(() -> client.fetchAuthorizationServerMetadata(AS_URI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("authorization_endpoint");
        }
    }
}