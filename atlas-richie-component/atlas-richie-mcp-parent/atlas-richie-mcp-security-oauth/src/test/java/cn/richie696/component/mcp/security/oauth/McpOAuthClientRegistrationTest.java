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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpOAuthClientRegistration} 单元测试：聚焦 RFC 7591 DCR 响应模型
 * 的 clientId 必填校验、不可变集合拷贝以及便捷构造器委托主构造器的行为。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpOAuthClientRegistration 动态注册响应模型")
class McpOAuthClientRegistrationTest {

    @Nested
    @DisplayName("紧凑构造器校验")
    class CompactConstructor {

        @Test
        @DisplayName("clientId 为 null 时抛出 IllegalArgumentException")
        void nullClientIdRejected() {
            assertThatThrownBy(() -> new McpOAuthClientRegistration(
                    null, "secret", "client_secret_basic",
                    List.of(URI.create("https://client/callback")),
                    Set.of("authorization_code"), Set.of("tools.read"),
                    null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("clientId");
        }

        @Test
        @DisplayName("clientId 为空白字符串时抛出 IllegalArgumentException")
        void blankClientIdRejected() {
            assertThatThrownBy(() -> new McpOAuthClientRegistration(
                    "  ", null, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("clientId");
        }

        @Test
        @DisplayName("集合字段为 null 时退化为空不可变集合")
        void nullCollectionsBecomeEmpty() {
            McpOAuthClientRegistration registration = new McpOAuthClientRegistration(
                    "client-1", null, null, null, null, null, null, null);

            assertThat(registration.redirectUris()).isEmpty();
            assertThat(registration.grantTypes()).isEmpty();
            assertThat(registration.scopes()).isEmpty();
        }

        @Test
        @DisplayName("传入可变集合后得到不可变拷贝")
        void mutableCollectionsAreDefensivelyCopied() {
            List<URI> redirects = new ArrayList<>();
            redirects.add(URI.create("https://client/callback"));
            Set<String> grants = new LinkedHashSet<>();
            grants.add("authorization_code");
            Set<String> scopes = new LinkedHashSet<>();
            scopes.add("tools.read");

            McpOAuthClientRegistration registration = new McpOAuthClientRegistration(
                    "client-1", null, null, redirects, grants, scopes, null, null);

            assertThatThrownBy(() -> registration.redirectUris().add(URI.create("https://evil/cb")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> registration.grantTypes().add("client_credentials"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> registration.scopes().add("admin"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("便捷构造器")
    class ConvenienceConstructor {

        @Test
        @DisplayName("省略注册管理端点字段时默认为 null")
        void delegatesAndNullsManagementFields() {
            McpOAuthClientRegistration registration = new McpOAuthClientRegistration(
                    "client-1", "secret", "client_secret_basic",
                    List.of(URI.create("https://client/callback")),
                    Set.of("authorization_code"),
                    Set.of("tools.read"));

            assertThat(registration.clientId()).isEqualTo("client-1");
            assertThat(registration.clientSecret()).isEqualTo("secret");
            assertThat(registration.tokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
            assertThat(registration.redirectUris()).containsExactly(URI.create("https://client/callback"));
            assertThat(registration.grantTypes()).containsExactly("authorization_code");
            assertThat(registration.scopes()).containsExactly("tools.read");
            assertThat(registration.registrationClientUri()).isNull();
            assertThat(registration.registrationAccessToken()).isNull();
        }
    }
}