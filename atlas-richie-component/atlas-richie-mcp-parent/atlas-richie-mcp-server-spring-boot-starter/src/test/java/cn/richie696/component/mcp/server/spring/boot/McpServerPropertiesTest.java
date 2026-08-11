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
package cn.richie696.component.mcp.server.spring.boot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验 {@link McpServerProperties} 全部 setter 校验、null 边界、不可变副本语义，
 * 覆盖顶层属性、{@link McpServerProperties.Tools}、{@link McpServerProperties.ToolDefaults}、
 * {@link McpServerProperties.ToolOverride}、{@link McpServerProperties.ToolDefinition}、{@link McpServerProperties.OAuth}。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpServerProperties 配置属性")
class McpServerPropertiesTest {

    @Nested
    @DisplayName("顶层属性 setter 校验")
    class TopLevel {

        @Test
        @DisplayName("path 必须以 '/' 开头，否则抛 IllegalArgumentException")
        void pathMustStartWithSlash() {
            McpServerProperties properties = new McpServerProperties();
            assertThatThrownBy(() -> properties.setPath(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'/'");
            assertThatThrownBy(() -> properties.setPath(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> properties.setPath("mcp"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'/'");
            assertThatNoException().isThrownBy(() -> properties.setPath("/mcp"));
            assertThat(properties.getPath()).isEqualTo("/mcp");
        }

        @Test
        @DisplayName("name / version 不可为空字符串")
        void nameAndVersionRequired() {
            McpServerProperties properties = new McpServerProperties();
            assertThatThrownBy(() -> properties.setName(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
            assertThatThrownBy(() -> properties.setName(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> properties.setVersion("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("version");
            properties.setName("svc");
            properties.setVersion("2.0.0");
            assertThat(properties.getName()).isEqualTo("svc");
            assertThat(properties.getVersion()).isEqualTo("2.0.0");
        }

        @Test
        @DisplayName("title/description/websiteUrl 允许为空，仅由 getter 透传")
        void optionalMetadataAllowsBlank() {
            McpServerProperties properties = new McpServerProperties();
            properties.setTitle("");
            properties.setDescription(null);
            properties.setWebsiteUrl("https://example.com");
            assertThat(properties.getTitle()).isEmpty();
            assertThat(properties.getDescription()).isNull();
            assertThat(properties.getWebsiteUrl()).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("allowedOrigins 为 null 时回退空集合，且 getter 返回不可变副本")
        void allowedOriginsImmutable() {
            McpServerProperties properties = new McpServerProperties();
            properties.setAllowedOrigins(null);
            assertThat(properties.getAllowedOrigins()).isEmpty();

            List<String> mutable = new java.util.ArrayList<>(List.of("https://a", "https://b"));
            properties.setAllowedOrigins(mutable);
            mutable.clear();
            assertThat(properties.getAllowedOrigins()).containsExactly("https://a", "https://b");
            assertThatThrownBy(() -> properties.getAllowedOrigins().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("oauth / tools 子配置为 null 时回退为默认实例")
        void oauthAndToolsNullFallback() {
            McpServerProperties properties = new McpServerProperties();
            properties.setOauth(null);
            properties.setTools(null);
            assertThat(properties.getOauth()).isNotNull();
            assertThat(properties.getTools()).isNotNull();
        }

        @Test
        @DisplayName("默认值的可见性：enabled=true / path=/mcp / name/version 含默认值")
        void defaultValuesAreSensible() {
            McpServerProperties properties = new McpServerProperties();
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getPath()).isEqualTo("/mcp");
            assertThat(properties.getName()).isEqualTo("atlas-richie-mcp-server");
            assertThat(properties.getVersion()).isEqualTo("1.0.0");
        }
    }

    @Nested
    @DisplayName("Tools 子配置 setter")
    class ToolsConfig {

        @Test
        @DisplayName("scanPackages / excludePackages 写入后为不可变副本")
        void packagesAreImmutableSnapshots() {
            McpServerProperties.Tools tools = new McpServerProperties.Tools();
            tools.setScanPackages(null);
            assertThat(tools.getScanPackages()).isEmpty();

            List<String> mutable = new java.util.ArrayList<>(List.of("com.a", "com.b"));
            tools.setScanPackages(mutable);
            mutable.clear();
            assertThat(tools.getScanPackages()).containsExactly("com.a", "com.b");

            tools.setExcludePackages(null);
            assertThat(tools.getExcludePackages()).isEmpty();
        }

        @Test
        @DisplayName("beanNames / excludeBeanNames / enabledGroups 写入后为不可变 Set 副本")
        void setsAreImmutableSnapshots() {
            McpServerProperties.Tools tools = new McpServerProperties.Tools();
            tools.setBeanNames(null);
            assertThat(tools.getBeanNames()).isEmpty();

            Set<String> mutable = new LinkedHashSet<>();
            mutable.add("a");
            mutable.add("b");
            tools.setBeanNames(mutable);
            mutable.clear();
            assertThat(tools.getBeanNames()).containsExactlyInAnyOrder("a", "b");
            assertThatThrownBy(() -> tools.getBeanNames().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);

            tools.setExcludeBeanNames(null);
            tools.setEnabledGroups(null);
            assertThat(tools.getExcludeBeanNames()).isEmpty();
            assertThat(tools.getEnabledGroups()).isEmpty();
        }

        @Test
        @DisplayName("definitions / overrides 写入后为不可变 Map 副本")
        void mapsAreImmutableSnapshots() {
            McpServerProperties.Tools tools = new McpServerProperties.Tools();
            Map<String, McpServerProperties.ToolDefinition> definitions = new LinkedHashMap<>();
            tools.setDefinitions(null);
            assertThat(tools.getDefinitions()).isEmpty();

            McpServerProperties.ToolDefinition def = new McpServerProperties.ToolDefinition();
            definitions.put("alpha", def);
            tools.setDefinitions(definitions);
            definitions.clear();
            assertThat(tools.getDefinitions()).containsOnlyKeys("alpha");
            assertThatThrownBy(() -> tools.getDefinitions().put("k", def))
                    .isInstanceOf(UnsupportedOperationException.class);

            tools.setOverrides(null);
            assertThat(tools.getOverrides()).isEmpty();
        }

        @Test
        @DisplayName("defaults 为 null 时回退为默认 ToolDefaults")
        void defaultsNullFallback() {
            McpServerProperties.Tools tools = new McpServerProperties.Tools();
            tools.setDefaults(null);
            assertThat(tools.getDefaults()).isNotNull();
            assertThat(tools.getDefaults().isAuditEnabled()).isFalse();
        }

        @Test
        @DisplayName("enabled / failFast / refreshEnabled 透传 boolean")
        void booleanFlags() {
            McpServerProperties.Tools tools = new McpServerProperties.Tools();
            assertThat(tools.isEnabled()).isTrue();
            assertThat(tools.isFailFast()).isTrue();
            assertThat(tools.isRefreshEnabled()).isFalse();
            tools.setEnabled(false);
            tools.setFailFast(false);
            tools.setRefreshEnabled(true);
            assertThat(tools.isEnabled()).isFalse();
            assertThat(tools.isFailFast()).isFalse();
            assertThat(tools.isRefreshEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("ToolDefaults / ToolOverride duration 校验")
    class DurationValidation {

        @Test
        @DisplayName("ToolDefaults.setTimeout 拒绝 0 和负数，null 允许")
        void defaultsTimeoutPositive() {
            McpServerProperties.ToolDefaults defaults = new McpServerProperties.ToolDefaults();
            assertThatThrownBy(() -> defaults.setTimeout(Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("timeout");
            assertThatThrownBy(() -> defaults.setTimeout(Duration.ofMillis(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatNoException().isThrownBy(() -> defaults.setTimeout(null));
            assertThatNoException().isThrownBy(() -> defaults.setTimeout(Duration.ofSeconds(30)));
            assertThat(defaults.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("ToolOverride.setTimeout 拒绝 0 和负数，null 允许")
        void overrideTimeoutPositive() {
            McpServerProperties.ToolOverride override = new McpServerProperties.ToolOverride();
            assertThatThrownBy(() -> override.setTimeout(Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("timeout");
            assertThatThrownBy(() -> override.setTimeout(Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatNoException().isThrownBy(() -> override.setTimeout(null));
            assertThatNoException().isThrownBy(() -> override.setTimeout(Duration.ofSeconds(5)));
            assertThat(override.getTimeout()).isEqualTo(Duration.ofSeconds(5));
        }
    }

    @Nested
    @DisplayName("ToolOverride 集合 / Map 不可变语义")
    class OverrideCollections {

        @Test
        @DisplayName("requiredScopes / annotations / policies / input/outputSchema 写入后为不可变副本")
        void overrideCollectionsAreImmutable() {
            McpServerProperties.ToolOverride override = new McpServerProperties.ToolOverride();
            assertThat(override.getRequiredScopes()).isNull();
            assertThat(override.getAnnotations()).isNull();
            assertThat(override.getPolicies()).isNull();
            assertThat(override.getInputSchema()).isNull();
            assertThat(override.getOutputSchema()).isNull();

            Set<String> scopes = new LinkedHashSet<>();
            scopes.add("read");
            override.setRequiredScopes(scopes);
            scopes.clear();
            assertThat(override.getRequiredScopes()).containsExactly("read");

            Map<String, Object> annotations = new LinkedHashMap<>();
            annotations.put("k", 1);
            override.setAnnotations(annotations);
            annotations.clear();
            assertThat(override.getAnnotations()).containsOnlyKeys("k");
            assertThatThrownBy(() -> override.getAnnotations().put("k2", 2))
                    .isInstanceOf(UnsupportedOperationException.class);

            override.setPolicies(null);
            override.setInputSchema(null);
            override.setOutputSchema(null);
            assertThat(override.getPolicies()).isNull();
            assertThat(override.getInputSchema()).isNull();
            assertThat(override.getOutputSchema()).isNull();
        }

        @Test
        @DisplayName("ToolOverride 简单字段透传（enabled / title / description / group / auditEnabled）")
        void overrideSimpleFieldsPassThrough() {
            McpServerProperties.ToolOverride override = new McpServerProperties.ToolOverride();
            override.setEnabled(true);
            override.setTitle("title");
            override.setDescription("desc");
            override.setGroup("g");
            override.setAuditEnabled(true);
            assertThat(override.getEnabled()).isTrue();
            assertThat(override.getTitle()).isEqualTo("title");
            assertThat(override.getDescription()).isEqualTo("desc");
            assertThat(override.getGroup()).isEqualTo("g");
            assertThat(override.getAuditEnabled()).isTrue();
        }

        @Test
        @DisplayName("ToolDefinition 继承 ToolOverride，并新增 handlerRef 字段")
        void toolDefinitionInheritsOverride() {
            McpServerProperties.ToolDefinition definition = new McpServerProperties.ToolDefinition();
            definition.setHandlerRef("price.query");
            definition.setEnabled(true);
            assertThat(definition.getHandlerRef()).isEqualTo("price.query");
            assertThat(definition.getEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("OAuth 子配置 setter")
    class OAuthConfig {

        @Test
        @DisplayName("resource 不可为空字符串")
        void resourceRequired() {
            McpServerProperties.OAuth oauth = new McpServerProperties.OAuth();
            assertThatThrownBy(() -> oauth.setResource(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("resource");
            assertThatThrownBy(() -> oauth.setResource("   "))
                    .isInstanceOf(IllegalArgumentException.class);
            oauth.setResource("https://mcp.example/mcp");
            assertThat(oauth.getResource()).isEqualTo("https://mcp.example/mcp");
        }

        @Test
        @DisplayName("authorizationServers / scopesSupported 写入后为不可变副本")
        void listsAreImmutable() {
            McpServerProperties.OAuth oauth = new McpServerProperties.OAuth();
            oauth.setAuthorizationServers(null);
            oauth.setScopesSupported(null);
            assertThat(oauth.getAuthorizationServers()).isEmpty();
            assertThat(oauth.getScopesSupported()).isEmpty();

            List<String> servers = new java.util.ArrayList<>(List.of("https://a", "https://b"));
            oauth.setAuthorizationServers(servers);
            servers.clear();
            assertThat(oauth.getAuthorizationServers()).containsExactly("https://a", "https://b");

            List<String> scopes = new java.util.ArrayList<>(List.of("read", "write"));
            oauth.setScopesSupported(scopes);
            scopes.clear();
            assertThat(oauth.getScopesSupported()).containsExactly("read", "write");
        }

        @Test
        @DisplayName("enabled 透传 boolean，默认 false")
        void enabledPassThrough() {
            McpServerProperties.OAuth oauth = new McpServerProperties.OAuth();
            assertThat(oauth.isEnabled()).isFalse();
            oauth.setEnabled(true);
            assertThat(oauth.isEnabled()).isTrue();
        }
    }
}
