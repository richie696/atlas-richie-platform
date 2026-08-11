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

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.schema.McpCompiledSchema;
import cn.richie696.component.mcp.schema.McpJsonSchemaValidator;
import cn.richie696.component.mcp.schema.McpJsonSchemaValidators;
import cn.richie696.component.mcp.schema.McpSchemaViolation;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpToolFixtures} 的三类工具：{@code callContext} 双参数/三参数形态、
 * {@code registration} 描述符组装以及 {@code assertValid} 校验断言（通过 / 失败）。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolFixtures Tool 测试夹具")
class McpToolFixturesTest {

    @Test
    @DisplayName("callContext 双参数形态：固定 requestId + V_2026_07_28 + scopes 空集")
    void callContextTwoArgBuildsExpectedFields() {
        McpCallContext context = McpToolFixtures.callContext("tenant-1", "user-A");

        assertThat(context.requestId()).isEqualTo("test-request");
        assertThat(context.protocolVersion()).isEqualTo(McpProtocolVersions.V_2026_07_28);
        assertThat(context.tenantId()).isEqualTo("tenant-1");
        assertThat(context.subject()).isEqualTo("user-A");
        assertThat(context.deadline()).isNull();
        assertThat(context.attributes()).containsKey("scopes");
        assertThat((Set<?>) context.attributes().get("scopes")).isEmpty();
    }

    @Test
    @DisplayName("callContext 三参数形态：null scopes 直接透传会抛 NPE（由内部 LinkedHashSet 抛出）")
    void callContextThreeArgDoesNotNormalizeNullScopes() {
        assertThatThrownBy(() -> McpToolFixtures.callContext(
                "tenant-1", "user-A", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("callContext 三参数形态：scopes 完整传递且保留顺序")
    void callContextThreeArgPassesScopesThrough() {
        McpCallContext context = McpToolFixtures.callContext(
                "tenant-1", "user-A", List.of("read", "write"));

        @SuppressWarnings("unchecked")
        Set<String> scopes = (Set<String>) context.attributes().get("scopes");
        assertThat(scopes).containsExactlyInAnyOrder("read", "write");
    }

    @Test
    @DisplayName("registration 同步 name 与 title，annotations 为空 Map")
    void registrationBuildsDescriptorWithConsistentNameAndTitle() {
        McpToolHandler handler = Mockito.mock(McpToolHandler.class);
        Map<String, Object> schema = Map.of("type", "object");

        McpToolRegistration registration = McpToolFixtures.registration(
                "echo", schema, schema, handler);

        McpToolDescriptor descriptor = registration.descriptor();
        assertThat(descriptor.name()).isEqualTo("echo");
        assertThat(descriptor.title()).isEqualTo("echo");
        assertThat(descriptor.description()).isEqualTo("Test fixture echo");
        assertThat(descriptor.inputSchema()).containsEntry("type", "object");
        assertThat(descriptor.outputSchema()).containsEntry("type", "object");
        assertThat(descriptor.annotations()).isEmpty();
        assertThat(registration.handler()).isSameAs(handler);
    }

    @Test
    @DisplayName("assertValid 在通过校验时静默成功")
    void assertValidPassesWhenValidationSucceeds() {
        McpJsonSchemaValidator validator = McpJsonSchemaValidators.secureDefaults();
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name"),
                "additionalProperties", false);

        assertThatCode(() -> McpToolFixtures.assertValid(
                validator, schema, Map.of("name", "Ada")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertValid 在校验失败时抛 AssertionError，错误信息含违规列表")
    void assertValidThrowsAssertionErrorOnViolation() {
        McpJsonSchemaValidator validator = McpJsonSchemaValidators.secureDefaults();
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name"),
                "additionalProperties", false);

        assertThatThrownBy(() -> McpToolFixtures.assertValid(
                validator, schema, Map.of("name", 123)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected valid MCP schema instance")
                .hasMessageContaining("violations=");
    }

    @Test
    @DisplayName("私有构造器不可被外部访问以避免被误实例化")
    void privateConstructorIsHidden() throws Exception {
        java.lang.reflect.Constructor<McpToolFixtures> constructor =
                McpToolFixtures.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("registration 与 assertValid 集成：构造的 schema 可被同一 validator 验证")
    void registrationSchemaRoundTripsThroughValidator() {
        McpJsonSchemaValidator validator = McpJsonSchemaValidators.secureDefaults();
        McpToolHandler handler = (arguments, context) ->
                CompletableFuture.completedFuture(new McpToolResponse(
                        List.of(), Map.of("echoed", arguments), false));

        McpToolRegistration registration = McpToolFixtures.registration(
                "echo",
                Map.of("type", "object", "properties", Map.of(
                        "text", Map.of("type", "string")),
                        "required", List.of("text"),
                        "additionalProperties", false),
                Map.of("type", "object"),
                handler);

        McpCompiledSchema compiled = validator.compile(registration.descriptor().inputSchema());
        assertThat(compiled.validate(Map.of("text", "hello")).isValid()).isTrue();
        assertThat(compiled.validate(Map.of()).violations())
                .extracting(McpSchemaViolation::keyword)
                .contains("required");
    }
}
