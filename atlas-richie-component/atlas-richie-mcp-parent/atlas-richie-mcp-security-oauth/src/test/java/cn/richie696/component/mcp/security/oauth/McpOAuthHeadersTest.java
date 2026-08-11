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

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpOAuthHeaders} 单元测试：覆盖 RFC 6750/9728 规定的
 * {@code Authorization: Bearer ...} 与 {@code WWW-Authenticate: Bearer ...} 头部格式，
 * 验证空参数回落、双引号转义与多 scope 空格拼接。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpOAuthHeaders 标准 HTTP Header 生成")
class McpOAuthHeadersTest {

    private static McpOAuthAccessToken sampleToken() {
        return new McpOAuthAccessToken(
                "eyJhbGc.secret", "Bearer",
                Instant.now().plusSeconds(60),
                "https://issuer.example", "https://mcp.example",
                Set.of("tools.read"));
    }

    @Nested
    @DisplayName("bearer()")
    class BearerHeader {

        @Test
        @DisplayName("委托给 token.authorizationHeader() 输出 Bearer 头值")
        void delegatesToToken() {
            assertThat(McpOAuthHeaders.bearer(sampleToken())).isEqualTo("Bearer eyJhbGc.secret");
        }

        @Test
        @DisplayName("token 为 null 时抛出 NullPointerException")
        void nullTokenRejected() {
            assertThatThrownBy(() -> McpOAuthHeaders.bearer(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("token");
        }
    }

    @Nested
    @DisplayName("unauthorizedChallenge()")
    class UnauthorizedChallenge {

        @Test
        @DisplayName("所有参数为空时仅输出 Bearer")
        void onlyBearerWhenAllEmpty() {
            assertThat(McpOAuthHeaders.unauthorizedChallenge(null, null)).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("resourceMetadataUri 为空白字符串时被忽略")
        void blankMetadataIgnored() {
            assertThat(McpOAuthHeaders.unauthorizedChallenge("   ", List.of("tools.read")))
                    .isEqualTo("Bearer scope=\"tools.read\"");
        }

        @Test
        @DisplayName("scopes 为空列表时被忽略")
        void emptyScopesIgnored() {
            assertThat(McpOAuthHeaders.unauthorizedChallenge(
                    "https://mcp.example/.well-known/oauth-protected-resource", List.of()))
                    .isEqualTo("Bearer resource_metadata=\"https://mcp.example/.well-known/oauth-protected-resource\"");
        }

        @Test
        @DisplayName("resourceMetadataUri 与 scopes 共同出现时按 Bearer / resource_metadata / scope 顺序")
        void combinedMetadataAndScopes() {
            assertThat(McpOAuthHeaders.unauthorizedChallenge(
                    "https://mcp.example/.well-known/oauth-protected-resource",
                    List.of("tools.read", "tools.write")))
                    .isEqualTo("Bearer resource_metadata=\"https://mcp.example/.well-known/oauth-protected-resource\" scope=\"tools.read tools.write\"");
        }

        @Test
        @DisplayName("scopes 元素中带引号时执行 RFC 6749 规定的反斜杠转义")
        void escapesQuotesAndBackslashes() {
            assertThat(McpOAuthHeaders.unauthorizedChallenge(
                    null, List.of("scope\"with-quote", "back\\slash")))
                    .isEqualTo("Bearer scope=\"scope\\\"with-quote back\\\\slash\"");
        }
    }
}