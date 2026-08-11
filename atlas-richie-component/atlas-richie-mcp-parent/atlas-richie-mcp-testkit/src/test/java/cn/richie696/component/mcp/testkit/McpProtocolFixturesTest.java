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
package cn.richie696.component.mcp.testkit;

import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpProtocolFixtures} 在两种典型协议版本下的请求形态：
 * 现代版（{@code 2026-07-28}）通过 {@code _meta} 注入协议版本/客户端信息/能力；
 * legacy 版（{@code 2025-11-25}）把这些信息作为 params 顶层字段直接传递。
 * 同时覆盖 {@code arguments=null} 时的空 Map 兜底逻辑。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpProtocolFixtures 协议夹具")
class McpProtocolFixturesTest {

    @Test
    @DisplayName("modernRequest() 通过 _meta 注入协议版本/客户端信息/能力")
    void modernRequestInjectsMetaKeys() {
        McpJsonRpcRequest request = McpProtocolFixtures.modernRequest(
                1, "tools/list", Map.of("cursor", "abc"));

        assertThat(request.jsonrpc()).isEqualTo("2.0");
        assertThat(request.id()).isEqualTo(1);
        assertThat(request.method()).isEqualTo("tools/list");
        Map<String, Object> params = request.params();
        assertThat(params).containsEntry("cursor", "abc");
        assertThat(params).containsKey("_meta");

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) params.get("_meta");
        assertThat(meta).containsEntry(McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28);
        @SuppressWarnings("unchecked")
        Map<String, Object> clientInfo =
                (Map<String, Object>) meta.get(McpMetaKeys.CLIENT_INFO);
        assertThat(clientInfo).containsEntry("name", "atlas-test-client");
        assertThat(clientInfo).containsEntry("version", "1.0.0");
        assertThat(meta).containsKey(McpMetaKeys.CLIENT_CAPABILITIES);
    }

    @Test
    @DisplayName("modernRequest() 在 arguments=null 时按空 Map 兜底，但 _meta 仍注入")
    void modernRequestHandlesNullArguments() {
        McpJsonRpcRequest request = McpProtocolFixtures.modernRequest(
                null, "notifications/initialized", null);

        assertThat(request.notification()).isTrue();
        assertThat(request.params()).containsKey("_meta");
        // 原始业务参数归一为空 Map
        assertThat(request.params()).doesNotContainKey("cursor");
    }

    @Test
    @DisplayName("legacyInitialize() 通过 params 顶层字段传递握手信息，且不包含 _meta")
    void legacyInitializeUsesParamsTopLevel() {
        McpJsonRpcRequest request = McpProtocolFixtures.legacyInitialize("42");

        assertThat(request.jsonrpc()).isEqualTo("2.0");
        assertThat(request.id()).isEqualTo("42");
        assertThat(request.method()).isEqualTo("initialize");
        Map<String, Object> params = request.params();
        assertThat(params).containsEntry("protocolVersion", McpProtocolVersions.V_2025_11_25);
        assertThat(params).containsKey("clientInfo");
        assertThat(params).containsKey("capabilities");
        assertThat(params).doesNotContainKey("_meta");
    }

    @Test
    @DisplayName("私有构造器不可被外部访问以避免被误实例化")
    void privateConstructorIsHidden() throws Exception {
        java.lang.reflect.Constructor<McpProtocolFixtures> constructor =
                McpProtocolFixtures.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("modernRequest() 接受 List 等复杂 arguments 类型，序列化前后类型稳定")
    void modernRequestAcceptsComplexArguments() {
        List<String> arguments = List.of("a", "b");
        McpJsonRpcRequest request = McpProtocolFixtures.modernRequest(
                1, "tools/call", Map.of("items", arguments));

        assertThat(request.params()).containsEntry("items", arguments);
        assertThat(request.params()).containsKey("_meta");
    }
}
