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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpOAuthUriPolicy} 单元测试：覆盖默认 {@link McpOAuthUriPolicy#httpsOnly()}
 * 策略的 scheme / userInfo / fragment 三类校验分支以及 lambda 形式自定义策略。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpOAuthUriPolicy URI SSRF 安全策略")
class McpOAuthUriPolicyTest {

    @Nested
    @DisplayName("httpsOnly() 默认策略")
    class HttpsOnlyPolicy {

        @Test
        @DisplayName("https scheme 通过校验")
        void httpsAccepted() {
            McpOAuthUriPolicy policy = McpOAuthUriPolicy.httpsOnly();

            assertThatCode(() -> policy.validate(URI.create("https://issuer.example/meta")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> policy.validate(URI.create("HTTPS://issuer.example/meta")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("http scheme 被拒绝并明确提示必须 HTTPS")
        void httpRejected() {
            McpOAuthUriPolicy policy = McpOAuthUriPolicy.httpsOnly();

            assertThatThrownBy(() -> policy.validate(URI.create("http://issuer.example/meta")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTPS");
        }

        @Test
        @DisplayName("file / gopher 等其他 scheme 同样被拒绝")
        void nonHttpSchemesRejected() {
            McpOAuthUriPolicy policy = McpOAuthUriPolicy.httpsOnly();

            assertThatThrownBy(() -> policy.validate(URI.create("file:///etc/passwd")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTPS");
            assertThatThrownBy(() -> policy.validate(URI.create("gopher://internal.svc")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTPS");
        }

        @Test
        @DisplayName("scheme 为 null 时被拒绝")
        void nullSchemeRejected() {
            McpOAuthUriPolicy policy = McpOAuthUriPolicy.httpsOnly();

            assertThatThrownBy(() -> policy.validate(URI.create("//issuer.example/path")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTPS");
        }

        @Test
        @DisplayName("URI 为 null 时抛 IllegalArgumentException")
        void nullUriRejected() {
            assertThatThrownBy(() -> McpOAuthUriPolicy.httpsOnly().validate(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTPS");
        }

        @Test
        @DisplayName("URI 携带 userInfo 被拒绝（凭据泄漏防护）")
        void userInfoRejected() {
            McpOAuthUriPolicy policy = McpOAuthUriPolicy.httpsOnly();

            assertThatThrownBy(() -> policy.validate(URI.create("https://user:pass@issuer.example/meta")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("credentials");
        }

        @Test
        @DisplayName("URI 携带 fragment 被拒绝")
        void fragmentRejected() {
            McpOAuthUriPolicy policy = McpOAuthUriPolicy.httpsOnly();

            assertThatThrownBy(() -> policy.validate(URI.create("https://issuer.example/meta#fragment")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("credentials or fragment");
        }
    }

    @Nested
    @DisplayName("自定义 lambda 策略")
    class CustomPolicy {

        @Test
        @DisplayName("始终通过的策略对任意 URI 都成功")
        void alwaysAllow() {
            McpOAuthUriPolicy policy = uri -> { };

            assertThatCode(() -> policy.validate(URI.create("http://anything")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> policy.validate(null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("始终拒绝的策略对任意 URI 都抛 IllegalArgumentException")
        void alwaysReject() {
            McpOAuthUriPolicy policy = uri -> {
                throw new IllegalArgumentException("deny");
            };

            assertThatThrownBy(() -> policy.validate(URI.create("https://x")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("deny");
        }
    }
}