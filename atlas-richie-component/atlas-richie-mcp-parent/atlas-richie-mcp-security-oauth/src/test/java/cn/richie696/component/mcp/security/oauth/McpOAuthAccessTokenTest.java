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

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpOAuthAccessToken} 单元测试：覆盖紧凑构造器的必填校验、默认 Bearer、不可变
 * scope 拷贝、{@link McpOAuthAccessToken#expired(Duration)} 时钟偏差语义与
 * {@link McpOAuthAccessToken#authorizationHeader()} 的拼接格式。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpOAuthAccessToken 访问令牌模型")
class McpOAuthAccessTokenTest {

    @Nested
    @DisplayName("紧凑构造器校验")
    class CompactConstructor {

        @Test
        @DisplayName("value 为 null 时抛出 IllegalArgumentException")
        void nullValueRejected() {
            assertThatThrownBy(() -> new McpOAuthAccessToken(
                    null, "Bearer", Instant.now().plusSeconds(60), null, null, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("value");
        }

        @Test
        @DisplayName("value 为空白字符串时抛出 IllegalArgumentException")
        void blankValueRejected() {
            assertThatThrownBy(() -> new McpOAuthAccessToken(
                    "   ", "Bearer", Instant.now().plusSeconds(60), null, null, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("value");
        }

        @Test
        @DisplayName("tokenType 为 null 或 blank 时默认填充为 Bearer")
        void tokenTypeDefaultsToBearer() {
            McpOAuthAccessToken nullType = new McpOAuthAccessToken(
                    "v", null, Instant.now().plusSeconds(60), null, null, null);
            McpOAuthAccessToken blankType = new McpOAuthAccessToken(
                    "v", "   ", Instant.now().plusSeconds(60), null, null, null);

            assertThat(nullType.tokenType()).isEqualTo("Bearer");
            assertThat(blankType.tokenType()).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("tokenType 非空时原样保留")
        void tokenTypePreservedWhenProvided() {
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "MAC", Instant.now().plusSeconds(60), null, null, null);

            assertThat(token.tokenType()).isEqualTo("MAC");
        }

        @Test
        @DisplayName("scopes 为 null 时退化为空 Set.of()")
        void nullScopesBecomeEmpty() {
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "Bearer", null, null, null, null);

            assertThat(token.scopes()).isEmpty();
        }

        @Test
        @DisplayName("传入可变 Set 后 scopes 不可变")
        void scopesAreDefensivelyCopied() {
            Set<String> source = new LinkedHashSet<>();
            source.add("tools.read");
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "Bearer", null, null, null, source);

            assertThatThrownBy(() -> token.scopes().add("admin"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("修改源 Set 不影响 record 副本")
        void sourceSetMutationDoesNotLeak() {
            Set<String> source = new LinkedHashSet<>();
            source.add("tools.read");
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "Bearer", null, null, null, source);

            source.add("admin");

            assertThat(token.scopes()).containsExactly("tools.read");
        }
    }

    @Nested
    @DisplayName("expired() 时钟偏差语义")
    class ExpiredSemantics {

        @Test
        @DisplayName("expiresAt 为 null 时视为永不过期")
        void nullExpiresAtIsNeverExpired() {
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "Bearer", null, null, null, null);

            assertThat(token.expired(Duration.ZERO)).isFalse();
            assertThat(token.expired(Duration.ofHours(1))).isFalse();
        }

        @Test
        @DisplayName("clockSkew 为 null 等价于 Duration.ZERO")
        void nullSkewIsZero() {
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "Bearer", Instant.now().minusSeconds(5), null, null, null);

            assertThat(token.expired(null)).isTrue();
        }

        @Test
        @DisplayName("expiresAt 早于 now+skew 时判定为过期")
        void pastExpiryIsExpired() {
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "Bearer", Instant.now().minusSeconds(10), null, null, null);

            assertThat(token.expired(Duration.ZERO)).isTrue();
        }

        @Test
        @DisplayName("expiresAt 晚于 now+skew 时仍未过期")
        void futureExpiryIsFresh() {
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "Bearer", Instant.now().plusSeconds(120), null, null, null);

            assertThat(token.expired(Duration.ofSeconds(5))).isFalse();
        }

        @Test
        @DisplayName("边界值：恰好等于 now+skew 视为已过期")
        void boundaryIsExpired() {
            Instant expiry = Instant.now();
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "Bearer", expiry, null, null, null);

            assertThat(token.expired(Duration.ZERO)).isTrue();
        }

        @Test
        @DisplayName("skew 内（即将过期 ≤ skew）的 token 视为已过期（保守策略）")
        void skewInsideWindowTreatedAsExpired() {
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "Bearer", Instant.now().plusSeconds(5), null, null, null);

            assertThat(token.expired(Duration.ofSeconds(10))).isTrue();
        }

        @Test
        @DisplayName("远在 skew 之外的未来到期时间仍判定为有效")
        void farFutureExpirySurvivesSkew() {
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "v", "Bearer", Instant.now().plusSeconds(120), null, null, null);

            assertThat(token.expired(Duration.ofSeconds(10))).isFalse();
        }
    }

    @Nested
    @DisplayName("authorizationHeader() 拼接")
    class AuthorizationHeader {

        @Test
        @DisplayName("输出 <type> <value> 拼接格式")
        void formatsAsExpected() {
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "abc.def.ghi", "Bearer", null, null, null, null);

            assertThat(token.authorizationHeader()).isEqualTo("Bearer abc.def.ghi");
        }

        @Test
        @DisplayName("自定义 tokenType 也参与拼接")
        void customTokenTypeUsed() {
            McpOAuthAccessToken token = new McpOAuthAccessToken(
                    "secret-token", "PoP", null, null, null, null);

            assertThat(token.authorizationHeader()).isEqualTo("PoP secret-token");
        }
    }
}