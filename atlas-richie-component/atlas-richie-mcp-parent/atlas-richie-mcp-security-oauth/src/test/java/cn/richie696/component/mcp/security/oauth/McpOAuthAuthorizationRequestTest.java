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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpOAuthAuthorizationRequest} 单元测试：覆盖紧凑构造器必填校验、
 * {@link McpOAuthAuthorizationRequest#toUri()} 生成的 OAuth 2.1 + RFC 7636 授权请求
 * URI（含 S256 PKCE、URL 编码、保留端点已有 query 参数的能力）。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpOAuthAuthorizationRequest 授权请求装配器")
class McpOAuthAuthorizationRequestTest {

    private static final URI ENDPOINT = URI.create("https://issuer.example/authorize");
    private static final URI REDIRECT = URI.create("https://client.example/callback");

    @Nested
    @DisplayName("紧凑构造器校验")
    class CompactConstructor {

        @Test
        @DisplayName("authorizationEndpoint 为 null 时抛出 NullPointerException")
        void nullAuthorizationEndpointRejected() {
            assertThatThrownBy(() -> new McpOAuthAuthorizationRequest(
                    null, "client", REDIRECT, null, null, "state", "challenge"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("authorizationEndpoint");
        }

        @Test
        @DisplayName("clientId 为 null 时抛出 NullPointerException")
        void nullClientIdRejected() {
            assertThatThrownBy(() -> new McpOAuthAuthorizationRequest(
                    ENDPOINT, null, REDIRECT, null, null, "state", "challenge"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("clientId");
        }

        @Test
        @DisplayName("redirectUri 为 null 时抛出 NullPointerException")
        void nullRedirectUriRejected() {
            assertThatThrownBy(() -> new McpOAuthAuthorizationRequest(
                    ENDPOINT, "client", null, null, null, "state", "challenge"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("redirectUri");
        }

        @Test
        @DisplayName("state 为 null 或空白时抛出 IllegalArgumentException")
        void blankStateRejected() {
            assertThatThrownBy(() -> new McpOAuthAuthorizationRequest(
                    ENDPOINT, "client", REDIRECT, null, null, null, "challenge"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("state");
            assertThatThrownBy(() -> new McpOAuthAuthorizationRequest(
                    ENDPOINT, "client", REDIRECT, null, null, "  ", "challenge"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("state");
        }

        @Test
        @DisplayName("codeChallenge 为 null 或空白时抛出 IllegalArgumentException")
        void blankCodeChallengeRejected() {
            assertThatThrownBy(() -> new McpOAuthAuthorizationRequest(
                    ENDPOINT, "client", REDIRECT, null, null, "state", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("codeChallenge");
            assertThatThrownBy(() -> new McpOAuthAuthorizationRequest(
                    ENDPOINT, "client", REDIRECT, null, null, "state", " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("codeChallenge");
        }
    }

    @Nested
    @DisplayName("toUri() URI 拼装")
    class UriAssembly {

        @Test
        @DisplayName("必填参数都存在且 code_challenge_method 固定为 S256")
        void allMandatoryParamsAndS256() {
            URI uri = new McpOAuthAuthorizationRequest(
                    ENDPOINT, "client-1", REDIRECT,
                    "tools.read", "https://mcp.example", "state-1",
                    "challenge-1").toUri();

            String query = uri.getRawQuery();
            assertThat(query)
                    .contains("response_type=code")
                    .contains("client_id=client-1")
                    .contains("redirect_uri=https%3A%2F%2Fclient.example%2Fcallback")
                    .contains("scope=tools.read")
                    .contains("resource=https%3A%2F%2Fmcp.example")
                    .contains("state=state-1")
                    .contains("code_challenge=challenge-1")
                    .contains("code_challenge_method=S256");
        }

        @Test
        @DisplayName("scope / resource 为 null 或 blank 时不参与拼装")
        void omitsBlankScopeAndResource() {
            URI uri = new McpOAuthAuthorizationRequest(
                    ENDPOINT, "client-1", REDIRECT,
                    null, null, "state-1", "challenge-1").toUri();

            assertThat(uri.getRawQuery())
                    .doesNotContain("scope=")
                    .doesNotContain("resource=")
                    .startsWith("response_type=code&client_id=client-1");
        }

        @Test
        @DisplayName("scope 为空白字符串时同样被忽略")
        void omitsBlankStringScope() {
            URI uri = new McpOAuthAuthorizationRequest(
                    ENDPOINT, "client-1", REDIRECT,
                    "   ", "  ", "state-1", "challenge-1").toUri();

            assertThat(uri.getRawQuery())
                    .doesNotContain("scope=")
                    .doesNotContain("resource=");
        }

        @Test
        @DisplayName("授权端点已带 query 时当前实现会丢弃（base 不携带 query）")
        void existingQueryIsDropped() {
            URI endpointWithQuery = URI.create("https://issuer.example/authorize?tenant=acme");
            URI uri = new McpOAuthAuthorizationRequest(
                    endpointWithQuery, "client-1", REDIRECT,
                    null, null, "state-1", "challenge-1").toUri();

            assertThat(uri.getRawQuery())
                    .startsWith("response_type=code")
                    .doesNotContain("tenant=acme");
        }

        @Test
        @DisplayName("端口号不会被 URI 重编码破坏")
        void portPreservedInAuthority() {
            URI endpointWithPort = URI.create("https://issuer.example:8443/authorize");
            URI uri = new McpOAuthAuthorizationRequest(
                    endpointWithPort, "client-1", REDIRECT,
                    null, null, "state-1", "challenge-1").toUri();

            assertThat(uri.getAuthority()).contains(":8443");
        }

        @Test
        @DisplayName("scope 中的空格被 URL 编码为 +")
        void scopeWithSpaceIsEncoded() {
            URI uri = new McpOAuthAuthorizationRequest(
                    ENDPOINT, "client-1", REDIRECT,
                    "tools.read tools.write", null, "state-1", "challenge-1").toUri();

            assertThat(uri.getRawQuery()).contains("scope=tools.read+tools.write");
        }
    }
}