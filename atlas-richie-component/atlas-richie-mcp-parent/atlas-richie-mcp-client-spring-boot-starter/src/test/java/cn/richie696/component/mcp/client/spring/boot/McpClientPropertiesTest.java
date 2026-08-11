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
package cn.richie696.component.mcp.client.spring.boot;

import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpClientProperties} 及其嵌套 {@link McpClientProperties.Server} /
 * {@link McpClientProperties.OAuth} 的全部 setter 校验：
 * 正数 Duration、必填字符串、枚举合法版本号、合法 maxPages/maxItems 范围。
 * 同时校验 null 边界（如 servers/oauth/scopes/headers 兜底为不可变空集合）。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpClientProperties 配置属性")
class McpClientPropertiesTest {

    @Nested
    @DisplayName("顶层 setter 校验")
    class TopLevelSetters {

        @Test
        @DisplayName("connectTimeout / requestTimeout / negotiationTtl / resultCacheTtl 强制为正数")
        void positiveDurationsRejectZeroOrNegative() {
            McpClientProperties properties = new McpClientProperties();
            assertThatThrownBy(() -> properties.setConnectTimeout(Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("connectTimeout");
            assertThatThrownBy(() -> properties.setRequestTimeout(Duration.ofMillis(-1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requestTimeout");
            assertThatThrownBy(() -> properties.setNegotiationTtl(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negotiationTtl");
            assertThatThrownBy(() -> properties.setResultCacheTtl(Duration.ofSeconds(0)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("resultCacheTtl");
        }

        @Test
        @DisplayName("name / version 不可为空字符串")
        void requiredStringsRejectBlank() {
            McpClientProperties properties = new McpClientProperties();
            assertThatThrownBy(() -> properties.setName(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
            assertThatThrownBy(() -> properties.setVersion("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("version");
        }

        @Test
        @DisplayName("preferredProtocolVersion 必须落在 SUPPORTED 列表内")
        void preferredProtocolVersionMustBeSupported() {
            McpClientProperties properties = new McpClientProperties();
            assertThatThrownBy(() -> properties.setPreferredProtocolVersion("1999-01-01"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported MCP preferredProtocolVersion");
            properties.setPreferredProtocolVersion(McpProtocolVersions.V_2025_11_25);
            assertThat(properties.getPreferredProtocolVersion()).isEqualTo(McpProtocolVersions.V_2025_11_25);
        }

        @Test
        @DisplayName("maxPages 越界（1..10000）和 maxItems 越界（1..1000000）抛错")
        void numericRangesAreEnforced() {
            McpClientProperties properties = new McpClientProperties();
            assertThatThrownBy(() -> properties.setMaxPages(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxPages");
            assertThatThrownBy(() -> properties.setMaxPages(10_001))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxPages");
            assertThatThrownBy(() -> properties.setMaxItems(-5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxItems");
            assertThatThrownBy(() -> properties.setMaxItems(1_000_001))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxItems");
        }

        @Test
        @DisplayName("servers 为 null 时按空 Map 兜底，且 getServers 返回不可变副本")
        void serversNullIsNormalizedAndImmutable() {
            McpClientProperties properties = new McpClientProperties();
            properties.setServers(null);

            assertThat(properties.getServers()).isEmpty();
            assertThatThrownBy(() -> properties.getServers().put("a", new McpClientProperties.Server()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("oauth 为 null 时回退为默认空 OAuth 配置")
        void oauthNullIsNormalized() {
            McpClientProperties properties = new McpClientProperties();
            properties.setOauth(null);
            assertThat(properties.getOauth()).isNotNull();
            assertThat(properties.getOauth().isEnabled()).isFalse();
            assertThat(properties.getOauth().getScopes()).isEmpty();
        }

        @Test
        @DisplayName("默认值的可见性：enabled=true / preferredProtocolVersion=V_2026_07_28")
        void defaultValuesAreSensible() {
            McpClientProperties properties = new McpClientProperties();
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getPreferredProtocolVersion())
                    .isEqualTo(McpProtocolVersions.V_2026_07_28);
            assertThat(properties.isNegotiateProtocol()).isTrue();
            assertThat(properties.isResultCacheEnabled()).isTrue();
            assertThat(properties.getResultCacheTtl()).isEqualTo(Duration.ofSeconds(60));
        }
    }

    @Nested
    @DisplayName("Server 子配置 setter")
    class ServerSetters {

        @Test
        @DisplayName("endpoint 不可为空字符串；resource/scopes/headers 为空值兜底")
        void endpointRequiredAndOptionalsNormalized() {
            McpClientProperties.Server server = new McpClientProperties.Server();
            assertThatThrownBy(() -> server.setEndpoint(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> server.setEndpoint("   "))
                    .isInstanceOf(IllegalArgumentException.class);
            server.setEndpoint("https://mcp.example.com/v1");
            server.setResource("");
            server.setScopes(null);
            server.setHeaders(null);

            assertThat(server.getEndpoint()).isEqualTo("https://mcp.example.com/v1");
            assertThat(server.getResource()).isNull();
            assertThat(server.getScopes()).isEmpty();
            assertThat(server.getHeaders()).isEmpty();
        }

        @Test
        @DisplayName("scopes / headers 写入后为不可变副本：外部突变不影响 getter")
        void scopesAndHeadersAreImmutableSnapshots() {
            McpClientProperties.Server server = new McpClientProperties.Server();
            server.setEndpoint("https://mcp.example.com");
            List<String> mutableScopes = new java.util.ArrayList<>(List.of("a", "b"));
            server.setScopes(mutableScopes);
            mutableScopes.clear();

            assertThat(server.getScopes()).containsExactly("a", "b");

            Map<String, String> mutableHeaders = new LinkedHashMap<>();
            mutableHeaders.put("X-1", "1");
            server.setHeaders(mutableHeaders);
            mutableHeaders.put("X-2", "2");

            assertThat(server.getHeaders()).containsOnlyKeys("X-1");
            assertThatThrownBy(() -> server.getHeaders().put("k", "v"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("OAuth 子配置 setter")
    class OAuthSetters {

        @Test
        @DisplayName("空白 tokenEndpoint/clientId/resource 规范化为 null；scopes null 兜底为不可变空集")
        void blankStringsNormalizedToNull() {
            McpClientProperties.OAuth oauth = new McpClientProperties.OAuth();
            oauth.setTokenEndpoint("  ");
            oauth.setClientId("");
            oauth.setResource("\t");
            oauth.setScopes(null);

            assertThat(oauth.getTokenEndpoint()).isNull();
            assertThat(oauth.getClientId()).isNull();
            assertThat(oauth.getResource()).isNull();
            assertThat(oauth.getScopes()).isEmpty();
            assertThatThrownBy(() -> oauth.getScopes().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("setScopes 会复制入参，外部突变不影响 getter")
        void scopesAreCopied() {
            McpClientProperties.OAuth oauth = new McpClientProperties.OAuth();
            Set<String> mutable = new java.util.LinkedHashSet<>();
            mutable.add("read");
            oauth.setScopes(mutable);
            mutable.clear();

            assertThat(oauth.getScopes()).containsExactly("read");
        }

        @Test
        @DisplayName("clientSecret 直接透传（不做归一），便于业务方自定义非空判断")
        void clientSecretPassesThrough() {
            McpClientProperties.OAuth oauth = new McpClientProperties.OAuth();
            oauth.setClientSecret("s3cret");
            assertThat(oauth.getClientSecret()).isEqualTo("s3cret");
        }
    }
}
