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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpOAuthIntrospectionResponse} 单元测试：覆盖 RFC 7662 introspection 响应
 * 模型的不可变 scopes 拷贝语义与紧凑构造器对 null 集合的归一化。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpOAuthIntrospectionResponse Introspection 响应模型")
class McpOAuthIntrospectionResponseTest {

    @Nested
    @DisplayName("紧凑构造器校验")
    class CompactConstructor {

        @Test
        @DisplayName("scopes 为 null 时退化为空 Set.of()")
        void nullScopesBecomeEmpty() {
            McpOAuthIntrospectionResponse response = new McpOAuthIntrospectionResponse(
                    true, "client-1", "user-1", "Bearer",
                    Instant.now().plusSeconds(60), null, "https://issuer.example", "https://mcp.example");

            assertThat(response.scopes()).isEmpty();
        }

        @Test
        @DisplayName("传入可变 Set 后 scopes 不可变")
        void scopesAreDefensivelyCopied() {
            Set<String> source = new LinkedHashSet<>();
            source.add("tools.read");

            McpOAuthIntrospectionResponse response = new McpOAuthIntrospectionResponse(
                    true, "client-1", null, "Bearer", null, source, null, null);

            assertThatThrownBy(() -> response.scopes().add("admin"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("包含 null 的 scopes 在 Set.copyOf 时会被拒绝（RFC 7662 对齐）")
        void scopesWithNullAreRejected() {
            Set<String> source = new HashSet<>();
            source.add(null);

            assertThatThrownBy(() -> new McpOAuthIntrospectionResponse(
                    true, null, null, "Bearer", null, source, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("全部字段合法时各字段均可读取")
        void happyPathFieldsAccessible() {
            Instant expiry = Instant.now().plusSeconds(120);
            McpOAuthIntrospectionResponse response = new McpOAuthIntrospectionResponse(
                    true, "client-1", "user-1", "Bearer", expiry,
                    Set.of("tools.read", "tools.write"),
                    "https://issuer.example", "https://mcp.example");

            assertThat(response.active()).isTrue();
            assertThat(response.clientId()).isEqualTo("client-1");
            assertThat(response.subject()).isEqualTo("user-1");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresAt()).isEqualTo(expiry);
            assertThat(response.scopes()).containsExactlyInAnyOrder("tools.read", "tools.write");
            assertThat(response.issuer()).isEqualTo("https://issuer.example");
            assertThat(response.resource()).isEqualTo("https://mcp.example");
        }
    }
}