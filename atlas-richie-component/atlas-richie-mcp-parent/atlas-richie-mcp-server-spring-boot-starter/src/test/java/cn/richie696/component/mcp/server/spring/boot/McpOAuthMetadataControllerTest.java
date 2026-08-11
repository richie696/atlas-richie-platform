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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpOAuthMetadataController} 启动期 eager 编码产物 + 非法 URI 防御。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpOAuthMetadataController 元数据端点")
class McpOAuthMetadataControllerTest {

    @Nested
    @DisplayName("构造与元数据生成")
    class Construction {

        @Test
        @DisplayName("构造时静态生成 RFC 9728 元数据，包含 resource / authorization_servers / scopes_supported")
        void buildsMetadataFromProperties() {
            McpServerProperties properties = new McpServerProperties();
            properties.getOauth().setResource("https://mcp.example.com/mcp");
            properties.getOauth().setAuthorizationServers(List.of("https://idp.example.com"));
            properties.getOauth().setScopesSupported(List.of("read", "write"));

            McpOAuthMetadataController controller = new McpOAuthMetadataController(properties);

            Map<String, Object> metadata = controller.get();
            assertThat(metadata)
                    .containsEntry("resource", "https://mcp.example.com/mcp")
                    .containsEntry("authorization_servers",
                            List.of("https://idp.example.com"))
                    .containsEntry("scopes_supported", List.of("read", "write"));
        }

        @Test
        @DisplayName("多授权服务器 / 空 scopes 场景的元数据组装（编码省略空 scopes_supported）")
        void multipleAuthorizationServersAndEmptyScopes() {
            McpServerProperties properties = new McpServerProperties();
            properties.getOauth().setResource("https://mcp.example.com");
            properties.getOauth().setAuthorizationServers(List.of(
                    "https://idp1.example.com", "https://idp2.example.com"));

            McpOAuthMetadataController controller = new McpOAuthMetadataController(properties);

            Map<String, Object> metadata = controller.get();
            assertThat(metadata)
                    .containsEntry("authorization_servers",
                            List.of("https://idp1.example.com", "https://idp2.example.com"))
                    .doesNotContainKey("scopes_supported");
        }

        @Test
        @DisplayName("resource 为非法 URI 时构造抛 IllegalArgumentException")
        void invalidResourceThrows() {
            McpServerProperties properties = new McpServerProperties();
            properties.getOauth().setResource("not a uri with spaces");

            assertThatThrownBy(() -> new McpOAuthMetadataController(properties))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("authorization_servers 列表中非法 URI 也抛 IllegalArgumentException")
        void invalidAuthorizationServerThrows() {
            McpServerProperties properties = new McpServerProperties();
            properties.getOauth().setResource("https://mcp.example.com");
            properties.getOauth().setAuthorizationServers(List.of("not a uri"));

            assertThatThrownBy(() -> new McpOAuthMetadataController(properties))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("get() 多次调用返回同一稳定 Map（identity 相等）")
        void getReturnsStableMap() {
            McpServerProperties properties = new McpServerProperties();
            properties.getOauth().setResource("https://mcp.example.com");

            McpOAuthMetadataController controller = new McpOAuthMetadataController(properties);

            assertThat(controller.get()).isSameAs(controller.get());
        }
    }
}
