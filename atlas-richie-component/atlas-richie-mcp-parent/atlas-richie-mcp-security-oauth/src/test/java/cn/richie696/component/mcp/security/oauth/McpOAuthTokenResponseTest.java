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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpOAuthTokenResponse} 单元测试：覆盖紧凑构造器的 accessToken 非空校验、
 * scopes 不可变拷贝以及 {@link McpOAuthTokenResponse#withRefreshTokenMetadata()} 的
 * scope 同步语义。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpOAuthTokenResponse token 端点响应模型")
class McpOAuthTokenResponseTest {

    private static McpOAuthAccessToken sampleToken(Set<String> scopes) {
        return new McpOAuthAccessToken(
                "at-1", "Bearer", Instant.now().plusSeconds(60),
                "https://issuer.example", "https://mcp.example", scopes);
    }

    @Nested
    @DisplayName("紧凑构造器校验")
    class CompactConstructor {

        @Test
        @DisplayName("accessToken 为 null 时抛 NullPointerException")
        void nullAccessTokenRejected() {
            assertThatThrownBy(() -> new McpOAuthTokenResponse(null, "rt-1", Set.of("tools.read")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("accessToken");
        }

        @Test
        @DisplayName("scopes 为 null 时退化为空 Set.of()")
        void nullScopesBecomeEmpty() {
            McpOAuthTokenResponse response = new McpOAuthTokenResponse(
                    sampleToken(Set.of("tools.read")), null, null);

            assertThat(response.scopes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("withRefreshTokenMetadata()")
    class WithRefreshTokenMetadata {

        @Test
        @DisplayName("响应级 scopes 覆盖 access token 内部 scopes")
        void overridesScopes() {
            McpOAuthAccessToken bare = sampleToken(Set.of());
            McpOAuthTokenResponse response = new McpOAuthTokenResponse(
                    bare, "rt-1", Set.of("tools.read", "tools.write"));

            McpOAuthAccessToken merged = response.withRefreshTokenMetadata();

            assertThat(merged.value()).isEqualTo(bare.value());
            assertThat(merged.tokenType()).isEqualTo(bare.tokenType());
            assertThat(merged.expiresAt()).isEqualTo(bare.expiresAt());
            assertThat(merged.issuer()).isEqualTo(bare.issuer());
            assertThat(merged.resource()).isEqualTo(bare.resource());
            assertThat(merged.scopes()).containsExactlyInAnyOrder("tools.read", "tools.write");
        }

        @Test
        @DisplayName("响应级 scopes 为空时也覆盖 token 的 scopes")
        void overridesEvenWhenEmpty() {
            McpOAuthAccessToken token = sampleToken(Set.of("tools.read"));
            McpOAuthTokenResponse response = new McpOAuthTokenResponse(token, null, Set.of());

            McpOAuthAccessToken merged = response.withRefreshTokenMetadata();

            assertThat(merged.scopes()).isEmpty();
        }
    }
}