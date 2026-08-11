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

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolDefinition;
import cn.richie696.component.mcp.api.server.McpToolDefinitionSource;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import cn.richie696.component.mcp.api.server.McpToolHandlerProvider;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import cn.richie696.component.mcp.transport.http.McpServerHttpEndpoint;
import com.example.mcp.InventoryToolFixture;
import com.example.mcp.OptionalPrimitiveToolFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpServerAutoConfiguration} 全部 {@code @Bean} 装配：Tool 注册表、控制器、
 * 拦截器、OAuth 元数据、refresh 桥接等。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpServerAutoConfiguration 自动装配")
class McpServerAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    McpServerAutoConfiguration.class));

    @Test
    @DisplayName("装配 Tool 注册表 + 端点 + 默认 Bean")
    void registersToolBeansAndCreatesEndpoint() {
        contextRunner
                .withBean(McpToolRegistration.class, () -> toolRegistration("echo"))
                .run(context -> {
                    assertThat(context).hasSingleBean(McpToolRegistry.class);
                    assertThat(context).hasSingleBean(McpServerHttpEndpoint.class);
                    assertThat(context.getBean(McpToolRegistry.class).revision()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("platform.component.mcp.server.enabled=false 时不装配 Server Bean")
    void disabledPropertySkipsServerBeans() {
        contextRunner
                .withPropertyValues("platform.component.mcp.server.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(McpToolRegistry.class));
    }

    @Test
    @DisplayName("业务自定义 McpToolRegistry 时优先使用，不装配 refresher/bridge")
    void customRegistryKeepsOwnershipAndIsNotAutomaticallyRefreshed() {
        McpToolRegistry custom = new McpToolRegistry();
        contextRunner
                .withBean("businessToolRegistry", McpToolRegistry.class, () -> custom)
                .withBean(McpToolRegistration.class, () -> toolRegistration("echo"))
                .withPropertyValues(
                        "platform.component.mcp.server.tools.refresh-enabled=true")
                .run(context -> {
                    assertThat(context.getBean(McpToolRegistry.class)).isSameAs(custom);
                    assertThat(custom.revision()).isZero();
                    assertThat(context).doesNotHaveBean(McpSpringToolDefinitionRefresher.class);
                    assertThat(context).doesNotHaveBean(McpToolRefreshEventBridge.class);
                });
    }

    @Test
    @DisplayName("OAuth 启用时暴露 /.well-known/oauth-protected-resource 元数据端点")
    void exposesProtectedResourceMetadataWhenOAuthIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "platform.component.mcp.server.oauth.enabled=true",
                        "platform.component.mcp.server.oauth.resource=https://mcp.example/mcp",
                        "platform.component.mcp.server.oauth.authorization-servers[0]=https://idp.example",
                        "platform.component.mcp.server.oauth.scopes-supported[0]=tools.read")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpOAuthMetadataController.class);
                    assertThat(context.getBean(McpOAuthMetadataController.class).get())
                            .containsEntry("resource", "https://mcp.example/mcp")
                            .containsEntry("authorization_servers", java.util.List.of("https://idp.example"));
                });
    }

    @Test
    @DisplayName("OAuth 启用但未配置 resource 时启动失败")
    void oauthWithoutResourceFails() {
        contextRunner
                .withPropertyValues("platform.component.mcp.server.oauth.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("oauth.resource");
                });
    }

    @Test
    @DisplayName("扫描注解 Tool：override.description / override.timeout 生效")
    void scansBindsAndOverridesComplexAnnotatedTool() {
        contextRunner
                .withBean("inventoryTool", InventoryToolFixture.class, InventoryToolFixture::new)
                .withPropertyValues(
                        "platform.component.mcp.server.tools.scan-packages[0]=com.example.mcp",
                        "platform.component.mcp.server.tools.overrides[inventory.query].description=live inventory",
                        "platform.component.mcp.server.tools.overrides[inventory.query].timeout=5s")
                .run(context -> {
                    McpToolRegistry registry = context.getBean(McpToolRegistry.class);
                    McpToolDescriptor descriptor = registry.snapshot(callContext()).tools().getFirst();
                    assertThat(descriptor.name()).isEqualTo("inventory.query");
                    assertThat(descriptor.description()).isEqualTo("live inventory");
                    assertThat(descriptor.annotations()).containsEntry("timeoutMs", 5_000L);
                    assertThat(descriptor.inputSchema().toString())
                            .contains("storeId", "itemCodes", "AVAILABLE");
                    assertThat(descriptor.outputSchema().toString())
                            .contains("tenantId", "itemCount");

                    McpToolResponse response = registry.requireAuthorized(
                                    "inventory.query", callContext())
                            .handler()
                            .handle(Map.of("request", Map.of(
                                    "storeId", "S-1",
                                    "itemCodes", java.util.List.of("I-1", "I-2"),
                                    "type", "AVAILABLE")), callContext())
                            .toCompletableFuture().join();
                    assertThat(response.structuredContent())
                            .isEqualTo(new InventoryToolFixture.InventoryResult(
                                    "tenant-1", "S-1", 2));
                });
    }

    @Test
    @DisplayName("YAML 声明 Tool 通过显式 handlerRef 解析到 Provider")
    void createsConfigurationBackedToolOnlyFromExplicitProvider() {
        McpToolHandlerProvider provider = new McpToolHandlerProvider() {
            @Override
            public String handlerRef() {
                return "price.query";
            }

            @Override
            public McpToolHandler handler() {
                return (arguments, callContext) -> CompletableFuture.completedFuture(
                        new McpToolResponse(java.util.List.of(), arguments, false));
            }
        };
        contextRunner
                .withBean(McpToolHandlerProvider.class, () -> provider)
                .withPropertyValues(
                        "platform.component.mcp.server.tools.definitions[external-price].handler-ref=price.query",
                        "platform.component.mcp.server.tools.definitions[external-price].description=price lookup",
                        "platform.component.mcp.server.tools.definitions[external-price].input-schema.type=object")
                .run(context -> {
                    McpToolRegistry registry = context.getBean(McpToolRegistry.class);
                    assertThat(registry.snapshot(callContext()).tools())
                            .extracting(McpToolDescriptor::name)
                            .containsExactly("external-price");
                });
    }

    @Test
    @DisplayName("override 指向未知 Tool 且 group 在 enabledGroups 之外时静默跳过")
    void ignoresUnknownOverrideThatExplicitlyBelongsToDisabledGroup() {
        contextRunner
                .withBean(McpToolRegistration.class, () -> toolRegistration("echo"))
                .withPropertyValues(
                        "platform.component.mcp.server.tools.enabled-groups[0]=generic",
                        "platform.component.mcp.server.tools.overrides[revenue.query].group=revenue",
                        "platform.component.mcp.server.tools.overrides[revenue.query].description=revenue")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(McpToolRegistry.class)
                            .snapshot(callContext()).tools()).isEmpty();
                });
    }

    @Test
    @DisplayName("primitive + defaultValue 的 @McpArgument 在 schema 中渲染为可选 + 数值默认")
    void primitiveWithDefaultIsOptionalAndUsesTypedSchemaDefault() {
        contextRunner
                .withBean("optionalPrimitiveTool", OptionalPrimitiveToolFixture.class,
                        OptionalPrimitiveToolFixture::new)
                .withPropertyValues(
                        "platform.component.mcp.server.tools.scan-packages[0]=com.example.mcp")
                .run(context -> {
                    McpToolRegistry registry = context.getBean(McpToolRegistry.class);
                    McpToolDescriptor descriptor = registry.snapshot(callContext()).tools().getFirst();
                    assertThat(descriptor.inputSchema()).doesNotContainKey("required");
                    assertThat(descriptor.inputSchema().toString())
                            .contains("default=3", "minimum=1", "maximum=7");

                    McpToolResponse response = registry.requireAuthorized("forecast", callContext())
                            .handler().handle(Map.of(), callContext())
                            .toCompletableFuture().join();
                    assertThat(response.structuredContent()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("override 指向未知 Tool 且 group 在 enabledGroups 之内时启动失败")
    void rejectsUnknownOverrideThatBelongsToEnabledGroup() {
        contextRunner
                .withPropertyValues(
                        "platform.component.mcp.server.tools.enabled-groups[0]=revenue",
                        "platform.component.mcp.server.tools.overrides[revenue.query].group=revenue")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasRootCauseMessage("Unknown MCP tool override: revenue.query"));
    }

    @Test
    @DisplayName("DefinitionSource 按 order 升序合并：通过受控 Handler + Scope 校验")
    void loadsOrderedExternalDefinitionSourceThroughControlledHandler() {
        McpToolHandlerProvider provider = new McpToolHandlerProvider() {
            @Override
            public String handlerRef() { return "external.query"; }

            @Override
            public McpToolHandler handler() {
                return (arguments, callContext) -> CompletableFuture.completedFuture(
                        new McpToolResponse(java.util.List.of(), arguments, false));
            }
        };
        McpToolDefinitionSource source = new McpToolDefinitionSource() {
            @Override
            public String sourceId() { return "database"; }

            @Override
            public int order() { return 100; }

            @Override
            public java.util.Collection<McpToolDefinition> load() {
                return java.util.List.of(new McpToolDefinition(
                        "external.dynamic", null, "dynamic definition", true,
                        "external.query", Map.of("type", "object"), Map.of(), Map.of(),
                        Set.of("external:read"), Duration.ofSeconds(3), "external", Map.of()));
            }
        };

        contextRunner
                .withBean(McpToolHandlerProvider.class, () -> provider)
                .withBean(McpToolDefinitionSource.class, () -> source)
                .run(context -> {
                    McpToolRegistry registry = context.getBean(McpToolRegistry.class);
                    McpCallContext authorized = new McpCallContext(
                            "request-1",
                            cn.richie696.component.mcp.protocol.McpProtocolVersions.V_2026_07_28,
                            "tenant-1", "subject-1", null,
                            Map.of("scopes", Set.of("external:read")), null, null);
                    assertThat(registry.snapshot(authorized).tools()).singleElement()
                            .satisfies(tool -> {
                                assertThat(tool.name()).isEqualTo("external.dynamic");
                                assertThat(tool.annotations()).containsEntry("timeoutMs", 3_000L);
                            });
                });
    }

    @Test
    @DisplayName("刷新：原子替换、相同内容不递增、失败保留旧状态")
    void refreshesAtomicallyIgnoresSameContentAndRetainsOldStateOnFailure() {
        contextRunner
                .withBean("inventoryTool", InventoryToolFixture.class, InventoryToolFixture::new)
                .withPropertyValues(
                        "platform.component.mcp.server.tools.scan-packages[0]=com.example.mcp",
                        "platform.component.mcp.server.tools.refresh-enabled=true",
                        "platform.component.mcp.server.tools.overrides[inventory.query].description=version one")
                .run(context -> {
                    McpToolRegistry registry = context.getBean(McpToolRegistry.class);
                    McpSpringToolDefinitionRefresher refresher =
                            context.getBean(McpSpringToolDefinitionRefresher.class);
                    assertThat(context).hasSingleBean(McpToolRefreshEventBridge.class);
                    long initialRevision = registry.revision();

                    TestPropertyValues.of(
                            "platform.component.mcp.server.tools.overrides[inventory.query].description=version two")
                            .applyTo(context.getEnvironment());
                    var changed = refresher.refresh();
                    assertThat(changed.changed()).isTrue();
                    assertThat(registry.snapshot(callContext()).tools().getFirst().description())
                            .isEqualTo("version two");

                    var unchanged = refresher.refresh();
                    assertThat(unchanged.changed()).isFalse();
                    assertThat(registry.revision()).isEqualTo(initialRevision + 1);

                    TestPropertyValues.of(
                            "platform.component.mcp.server.tools.overrides[inventory.query].input-schema.type=string")
                            .applyTo(context.getEnvironment());
                    assertThatThrownBy(refresher::refresh)
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("root type must be object");
                    assertThat(registry.revision()).isEqualTo(initialRevision + 1);
                    assertThat(registry.snapshot(callContext()).tools().getFirst().description())
                            .isEqualTo("version two");
                    assertThat(refresher.status().successful()).isFalse();
                });
    }

    private McpToolRegistration toolRegistration(String name) {
        McpToolDescriptor descriptor = new McpToolDescriptor(
                name,
                null,
                "Echo input",
                Map.of("type", "object"),
                Map.of(),
                Map.of());
        McpToolHandler handler = (arguments, callContext) ->
                CompletableFuture.completedFuture(new McpToolResponse(
                        java.util.List.of(Map.of("type", "text", "text", "ok")),
                        Map.of(),
                        false));
        return new McpToolRegistration(descriptor, handler);
    }

    private McpCallContext callContext() {
        return new McpCallContext(
                "request-1",
                cn.richie696.component.mcp.protocol.McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "subject-1",
                null,
                Map.of(),
                null,
                null);
    }
}
