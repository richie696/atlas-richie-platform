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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpProtectedResourceMetadataCodec} 单元测试：验证 RFC 9728 PRM 字段命名（snake_case）、
 * 厂商扩展字段透传、空集合省略以及 metadata 为 null 的非空校验。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpProtectedResourceMetadataCodec PRM JSON 编解码")
class McpProtectedResourceMetadataCodecTest {

    private static final URI RESOURCE = URI.create("https://mcp.example/mcp");

    @Nested
    @DisplayName("encode() 编码")
    class Encode {

        @Test
        @DisplayName("标准字段按 RFC 9728 snake_case 输出，并保留扩展字段")
        void encodesRfc9728FieldNames() {
            Map<String, Object> wire = McpProtectedResourceMetadataCodec.encode(
                    new McpProtectedResourceMetadata(
                            RESOURCE,
                            List.of(URI.create("https://idp.example")),
                            List.of("tools.read"),
                            Map.of("x-tenant", "required")));

            assertThat(wire).containsEntry("resource", "https://mcp.example/mcp")
                    .containsEntry("authorization_servers", List.of("https://idp.example"))
                    .containsEntry("scopes_supported", List.of("tools.read"))
                    .containsEntry("x-tenant", "required");
        }

        @Test
        @DisplayName("authorizationServers 为空集合时该字段被省略")
        void emptyAuthorizationServersIsOmitted() {
            Map<String, Object> wire = McpProtectedResourceMetadataCodec.encode(
                    new McpProtectedResourceMetadata(
                            RESOURCE,
                            List.of(),
                            List.of("tools.read"),
                            Map.of()));

            assertThat(wire).containsOnlyKeys("resource", "scopes_supported");
            assertThat(wire.get("resource")).isEqualTo("https://mcp.example/mcp");
        }

        @Test
        @DisplayName("scopesSupported 为空集合时该字段被省略")
        void emptyScopesAreOmitted() {
            Map<String, Object> wire = McpProtectedResourceMetadataCodec.encode(
                    new McpProtectedResourceMetadata(
                            RESOURCE,
                            List.of(URI.create("https://idp.example")),
                            List.of(),
                            Map.of()));

            assertThat(wire).containsOnlyKeys("resource", "authorization_servers");
        }

        @Test
        @DisplayName("extensions 中含多个厂商字段时全部透传")
        void multipleExtensionFieldsPassedThrough() {
            Map<String, Object> extensions = new LinkedHashMap<>();
            extensions.put("x-tenant", "required");
            extensions.put("bearer_methods_supported", List.of("header"));
            extensions.put("resource_documentation", "https://docs.example/mcp");

            Map<String, Object> wire = McpProtectedResourceMetadataCodec.encode(
                    new McpProtectedResourceMetadata(
                            RESOURCE, List.of(), List.of(), extensions));

            assertThat(wire).containsEntry("x-tenant", "required")
                    .containsEntry("bearer_methods_supported", List.of("header"))
                    .containsEntry("resource_documentation", "https://docs.example/mcp");
        }

        @Test
        @DisplayName("编码结果 Map 不可变")
        void encodeResultIsImmutable() {
            Map<String, Object> wire = McpProtectedResourceMetadataCodec.encode(
                    new McpProtectedResourceMetadata(
                            RESOURCE, List.of(), List.of(), Map.of("x", "1")));

            assertThatThrownBy(() -> wire.put("x", "tampered"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("metadata 为 null 时抛 NullPointerException")
        void nullMetadataRejected() {
            assertThatThrownBy(() -> McpProtectedResourceMetadataCodec.encode(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("metadata");
        }

        @Test
        @DisplayName("多个 AS URI 时按列表顺序输出字符串数组")
        void multipleAsUrisSerializedAsStrings() {
            Map<String, Object> wire = McpProtectedResourceMetadataCodec.encode(
                    new McpProtectedResourceMetadata(
                            RESOURCE,
                            List.of(URI.create("https://idp1"), URI.create("https://idp2")),
                            List.of(),
                            Map.of()));

            assertThat(wire.get("authorization_servers"))
                    .isEqualTo(List.of("https://idp1", "https://idp2"));
        }
    }
}