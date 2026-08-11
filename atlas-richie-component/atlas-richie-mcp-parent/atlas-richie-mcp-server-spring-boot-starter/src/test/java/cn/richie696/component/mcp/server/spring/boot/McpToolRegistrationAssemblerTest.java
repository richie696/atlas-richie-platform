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
import cn.richie696.component.mcp.schema.JacksonMcpTypeSchemaGenerator;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link McpToolRegistrationAssembler} 的多源合并、override 覆写、DefinitionSource
 * 排序、fail-fast / fail-soft 切换、enabled / enabledGroups 过滤。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolRegistrationAssembler 多源装配")
class McpToolRegistrationAssemblerTest {

    @Nested
    @DisplayName("构造校验")
    class Construction {

        @Test
        @DisplayName("annotatedRegistrar / properties 为 null 抛 NullPointerException")
        void nullArgsRejected() {
            assertThatThrownBy(() -> new McpToolRegistrationAssembler(
                    List.of(), null, List.of(), List.of(), new McpServerProperties()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("annotatedRegistrar");
            assertThatThrownBy(() -> new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(), List.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("properties");
        }

        @Test
        @DisplayName("handlerRef 为空抛 IllegalArgumentException")
        void blankHandlerRefRejected() {
            McpToolHandlerProvider provider = new McpToolHandlerProvider() {
                @Override
                public String handlerRef() {
                    return "  ";
                }

                @Override
                public McpToolHandler handler() {
                    return (args, ctx) -> CompletableFuture.completedFuture(
                            new McpToolResponse(List.of(), "ok", false));
                }
            };
            assertThatThrownBy(() -> new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(provider), List.of(), new McpServerProperties()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("handlerRef");
        }

        @Test
        @DisplayName("handler 为 null 抛 NullPointerException")
        void nullHandlerRejected() {
            McpToolHandlerProvider provider = new McpToolHandlerProvider() {
                @Override
                public String handlerRef() {
                    return "valid";
                }

                @Override
                public McpToolHandler handler() {
                    return null;
                }
            };
            assertThatThrownBy(() -> new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(provider), List.of(), new McpServerProperties()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("handlerRef 重复抛 IllegalArgumentException")
        void duplicateHandlerRefRejected() {
            McpToolHandlerProvider a = provider("ref", (args, ctx) -> CompletableFuture.completedFuture(
                    new McpToolResponse(List.of(), "a", false)));
            McpToolHandlerProvider b = provider("ref", (args, ctx) -> CompletableFuture.completedFuture(
                    new McpToolResponse(List.of(), "b", false)));
            assertThatThrownBy(() -> new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(a, b), List.of(), new McpServerProperties()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate");
        }
    }

    @Nested
    @DisplayName("assemble 合并与覆写")
    class Assemble {

        @Test
        @DisplayName("tools.enabled=false 时仅返回 legacyRegistrations")
        void toolsDisabledReturnsLegacyOnly() {
            McpToolRegistration legacy = legacy("legacy");
            McpServerProperties properties = new McpServerProperties();
            properties.getTools().setEnabled(false);

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(), List.of(), properties);

            assertThat(assembler.assemble())
                    .extracting(r -> r.descriptor().name())
                    .containsExactly("legacy");
        }

        @Test
        @DisplayName("definitions 合法 handlerRef 产出注册项")
        void definitionsResolvesHandler() {
            McpToolHandlerProvider provider = provider("price.query",
                    (args, ctx) -> CompletableFuture.completedFuture(
                            new McpToolResponse(List.of(), "ok", false)));
            McpServerProperties properties = new McpServerProperties();
            McpServerProperties.ToolDefinition def = new McpServerProperties.ToolDefinition();
            def.setHandlerRef("price.query");
            def.setDescription("price lookup");
            properties.getTools().setDefinitions(Map.of("price.lookup", def));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(provider), List.of(), properties);

            assertThat(assembler.assemble())
                    .extracting(r -> r.descriptor().name())
                    .containsExactly("price.lookup");
        }

        @Test
        @DisplayName("definitions 未知 handlerRef fail-fast=true 抛 IllegalArgumentException")
        void definitionsUnknownHandlerRefFailsFast() {
            McpServerProperties properties = new McpServerProperties();
            McpServerProperties.ToolDefinition def = new McpServerProperties.ToolDefinition();
            def.setHandlerRef("unknown");
            properties.getTools().setDefinitions(Map.of("foo", def));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(), List.of(), properties);

            assertThatThrownBy(assembler::assemble)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown MCP tool handlerRef");
        }

        @Test
        @DisplayName("definitions 未知 handlerRef fail-fast=false 不抛错，仅跳过")
        void definitionsUnknownHandlerRefFailsSoft() {
            McpServerProperties properties = new McpServerProperties();
            properties.getTools().setFailFast(false);
            McpServerProperties.ToolDefinition def = new McpServerProperties.ToolDefinition();
            def.setHandlerRef("unknown");
            properties.getTools().setDefinitions(Map.of("foo", def));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(), List.of(), properties);

            assertThat(assembler.assemble()).isEmpty();
        }

        @Test
        @DisplayName("definitions handlerRef 为空抛 IllegalArgumentException")
        void definitionsBlankHandlerRefRejected() {
            McpServerProperties properties = new McpServerProperties();
            McpServerProperties.ToolDefinition def = new McpServerProperties.ToolDefinition();
            def.setHandlerRef("");
            properties.getTools().setDefinitions(Map.of("foo", def));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(), List.of(), properties);

            assertThatThrownBy(assembler::assemble)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("handler-ref");
        }

        @Test
        @DisplayName("同名 Tool 来自多源（fail-fast）抛 IllegalArgumentException")
        void duplicateNameFailsFast() {
            McpToolRegistration legacy = legacy("dup");
            McpToolRegistration other = legacy("dup");
            McpServerProperties properties = new McpServerProperties();

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy, other), newRegistrar(), List.of(), List.of(), properties);

            assertThatThrownBy(assembler::assemble)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate MCP tool name");
        }

        @Test
        @DisplayName("overrides.title 覆写 descriptor.title")
        void overrideTitle() {
            McpToolRegistration legacy = legacy("tool", "original");
            McpServerProperties properties = new McpServerProperties();
            McpServerProperties.ToolOverride override = new McpServerProperties.ToolOverride();
            override.setTitle("new title");
            properties.getTools().setOverrides(Map.of("tool", override));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(), List.of(), properties);

            McpToolRegistration result = assembler.assemble().stream()
                    .filter(r -> r.descriptor().name().equals("tool"))
                    .findFirst().orElseThrow();
            assertThat(result.descriptor().title()).isEqualTo("new title");
        }

        @Test
        @DisplayName("overrides.description 覆写 descriptor.description")
        void overrideDescription() {
            McpToolRegistration legacy = legacy("tool", "original");
            McpServerProperties properties = new McpServerProperties();
            McpServerProperties.ToolOverride override = new McpServerProperties.ToolOverride();
            override.setDescription("new desc");
            properties.getTools().setOverrides(Map.of("tool", override));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(), List.of(), properties);

            McpToolRegistration result = assembler.assemble().stream()
                    .filter(r -> r.descriptor().name().equals("tool"))
                    .findFirst().orElseThrow();
            assertThat(result.descriptor().description()).isEqualTo("new desc");
        }

        @Test
        @DisplayName("overrides.fields.inputSchema 覆写 inputSchema")
        void overrideInputSchema() {
            McpToolRegistration legacy = legacy("tool");
            McpServerProperties properties = new McpServerProperties();
            McpServerProperties.ToolOverride override = new McpServerProperties.ToolOverride();
            override.setInputSchema(Map.of("type", "string"));
            properties.getTools().setOverrides(Map.of("tool", override));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(), List.of(), properties);

            McpToolRegistration result = assembler.assemble().stream()
                    .filter(r -> r.descriptor().name().equals("tool"))
                    .findFirst().orElseThrow();
            assertThat(result.descriptor().inputSchema()).containsEntry("type", "string");
        }

        @Test
        @DisplayName("overrides 未知 tool（group 在 enabledGroups 之外）静默跳过")
        void overrideUnknownToolOutsideEnabledGroup() {
            McpServerProperties properties = new McpServerProperties();
            properties.getTools().setEnabledGroups(Set.of("generic"));
            McpServerProperties.ToolOverride override = new McpServerProperties.ToolOverride();
            override.setGroup("revenue");
            override.setDescription("revenue");
            properties.getTools().setOverrides(Map.of("missing", override));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(), List.of(), properties);

            assertThat(assembler.assemble()).isEmpty();
        }

        @Test
        @DisplayName("overrides 未知 tool（group 在 enabledGroups 之内）fail-fast=true 抛错")
        void overrideUnknownToolInEnabledGroupFailsFast() {
            McpServerProperties properties = new McpServerProperties();
            properties.getTools().setEnabledGroups(Set.of("revenue"));
            McpServerProperties.ToolOverride override = new McpServerProperties.ToolOverride();
            override.setGroup("revenue");
            properties.getTools().setOverrides(Map.of("missing", override));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(), List.of(), properties);

            assertThatThrownBy(assembler::assemble)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown MCP tool override");
        }

        @Test
        @DisplayName("enabled=false 的 Tool 在最终过滤中被丢弃")
        void enabledFalseFilteredOut() {
            McpToolRegistration legacy = legacy("tool");
            McpServerProperties properties = new McpServerProperties();
            McpServerProperties.ToolOverride override = new McpServerProperties.ToolOverride();
            override.setEnabled(false);
            properties.getTools().setOverrides(Map.of("tool", override));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(), List.of(), properties);

            assertThat(assembler.assemble()).isEmpty();
        }

        @Test
        @DisplayName("tool 不在 enabledGroups 时被过滤")
        void notInEnabledGroupsFilteredOut() {
            McpToolRegistration legacy = legacy("tool", "default", Map.of("group", "other"));
            McpServerProperties properties = new McpServerProperties();
            properties.getTools().setEnabledGroups(Set.of("wanted"));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(), List.of(), properties);

            assertThat(assembler.assemble()).isEmpty();
        }

        @Test
        @DisplayName("tool 在 enabledGroups 时保留")
        void inEnabledGroupsRetained() {
            McpToolRegistration legacy = legacy("tool", "default", Map.of("group", "wanted"));
            McpServerProperties properties = new McpServerProperties();
            properties.getTools().setEnabledGroups(Set.of("wanted"));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(), List.of(), properties);

            assertThat(assembler.assemble()).extracting(r -> r.descriptor().name())
                    .containsExactly("tool");
        }
    }

    @Nested
    @DisplayName("DefinitionSource 合并")
    class DefinitionSource {

        @Test
        @DisplayName("按 order 升序合并，相同 order 内按 sourceId 字典序")
        void sourcesMergedInOrder() {
            McpToolDefinitionSource first = source("alpha", 10, new McpToolDefinition(
                    "alpha-tool", null, "alpha desc", true, "h1",
                    Map.of("type", "object"), Map.of(), Map.of(), Set.of(),
                    Duration.ofSeconds(1), null, Map.of()));
            McpToolDefinitionSource second = source("beta", 10, new McpToolDefinition(
                    "beta-tool", null, "beta desc", true, "h2",
                    Map.of("type", "object"), Map.of(), Map.of(), Set.of(),
                    Duration.ofSeconds(1), null, Map.of()));
            McpToolDefinitionSource third = source("gamma", 20, new McpToolDefinition(
                    "gamma-tool", null, "gamma desc", true, "h3",
                    Map.of("type", "object"), Map.of(), Map.of(), Set.of(),
                    Duration.ofSeconds(1), null, Map.of()));
            McpServerProperties properties = new McpServerProperties();
            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(handler("h1"), handler("h2"), handler("h3")),
                    List.of(third, first, second), properties);

            assertThat(assembler.assemble())
                    .extracting(r -> r.descriptor().name())
                    .containsExactly("alpha-tool", "beta-tool", "gamma-tool");
        }

        @Test
        @DisplayName("definition source enabled=false 时移除同名 Tool")
        void sourceDisablesRemovesTool() {
            McpToolRegistration legacy = legacy("tool");
            McpToolDefinitionSource source = source("src", 0, new McpToolDefinition(
                    "tool", null, null, false, null,
                    Map.of(), Map.of(), Map.of(), Set.of(), null, null, Map.of()));
            McpServerProperties properties = new McpServerProperties();

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(),
                    List.of(source), properties);

            assertThat(assembler.assemble()).isEmpty();
        }

        @Test
        @DisplayName("definition source 携带与已有 Tool 不同的 handlerRef 时 fail-fast")
        void sourceMismatchedHandlerRefFailsFast() {
            McpToolRegistration legacy = legacy("tool", "default", Map.of("handlerRef", "h1"));
            McpToolDefinitionSource source = source("src", 0, new McpToolDefinition(
                    "tool", null, null, true, "h2",
                    Map.of(), Map.of(), Map.of(), Set.of(), null, null, Map.of()));
            McpServerProperties properties = new McpServerProperties();

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(handler("h1"), handler("h2")),
                    List.of(source), properties);

            assertThatThrownBy(assembler::assemble)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("handler of existing tool");
        }

        @Test
        @DisplayName("definition source handlerRef 未注册时抛 IllegalArgumentException")
        void sourceUnknownHandlerRefFailsFast() {
            McpToolDefinitionSource source = source("src", 0, new McpToolDefinition(
                    "tool", null, null, true, "unknown",
                    Map.of(), Map.of(), Map.of(), Set.of(), null, null, Map.of()));
            McpServerProperties properties = new McpServerProperties();

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(), List.of(source), properties);

            assertThatThrownBy(assembler::assemble)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown MCP tool handlerRef");
        }

        @Test
        @DisplayName("同一 order 内的 source 出现同名 Tool 时 fail-fast")
        void sameOrderDuplicateFailsFast() {
            McpToolDefinitionSource first = source("a-source", 0, new McpToolDefinition(
                    "tool", null, null, true, "h1",
                    Map.of(), Map.of(), Map.of(), Set.of(), null, null, Map.of()));
            McpToolDefinitionSource second = source("b-source", 0, new McpToolDefinition(
                    "tool", null, null, true, "h2",
                    Map.of(), Map.of(), Map.of(), Set.of(), null, null, Map.of()));
            McpServerProperties properties = new McpServerProperties();

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(handler("h1"), handler("h2")),
                    List.of(first, second), properties);

            assertThatThrownBy(assembler::assemble)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate MCP tool definition");
        }

        @Test
        @DisplayName("definition source load 返回 null 抛 NullPointerException")
        void sourceReturnsNullFails() {
            McpToolDefinitionSource source = mock(McpToolDefinitionSource.class);
            when(source.sourceId()).thenReturn("bad");
            when(source.order()).thenReturn(0);
            when(source.load()).thenReturn(null);
            McpServerProperties properties = new McpServerProperties();

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(), List.of(source), properties);

            assertThatThrownBy(assembler::assemble)
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("defaults 应用")
    class Defaults {

        @Test
        @DisplayName("ToolDefaults.auditEnabled=true 写入 audit=true（缺省时）")
        void defaultsAuditApplied() {
            McpToolRegistration legacy = legacy("tool");
            McpServerProperties properties = new McpServerProperties();
            properties.getTools().getDefaults().setAuditEnabled(true);

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(), List.of(), properties);

            McpToolRegistration result = assembler.assemble().getFirst();
            assertThat(result.descriptor().annotations()).containsEntry("audit", true);
        }

        @Test
        @DisplayName("ToolDefaults.timeout 写入 timeoutMs（缺省时）")
        void defaultsTimeoutApplied() {
            McpToolRegistration legacy = legacy("tool");
            McpServerProperties properties = new McpServerProperties();
            properties.getTools().getDefaults().setTimeout(Duration.ofSeconds(2));

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(), List.of(), properties);

            McpToolRegistration result = assembler.assemble().getFirst();
            assertThat(result.descriptor().annotations()).containsEntry("timeoutMs", 2000L);
        }

        @Test
        @DisplayName("ToolDefaults 缺省时 audit 默认为 false、timeout 为 null（不写入）")
        void defaultsEmpty() {
            McpToolRegistration legacy = legacy("tool");
            McpServerProperties properties = new McpServerProperties();

            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(legacy), newRegistrar(), List.of(), List.of(), properties);

            McpToolRegistration result = assembler.assemble().getFirst();
            assertThat(result.descriptor().annotations()).containsEntry("audit", false);
            assertThat(result.descriptor().annotations()).doesNotContainKey("timeoutMs");
        }
    }

    @Nested
    @DisplayName("updateProperties")
    class UpdateProperties {

        @Test
        @DisplayName("updateProperties(null) 抛 NullPointerException")
        void nullRejected() {
            McpToolRegistrationAssembler assembler = new McpToolRegistrationAssembler(
                    List.of(), newRegistrar(), List.of(), List.of(), new McpServerProperties());
            assertThatThrownBy(() -> assembler.updateProperties(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    private static McpAnnotatedToolRegistrar newRegistrar() {
        return new McpAnnotatedToolRegistrar(
                new AnnotationConfigApplicationContext());
    }

    private static McpToolRegistration legacy(String name) {
        return legacy(name, "default", Map.of());
    }

    private static McpToolRegistration legacy(String name, String description) {
        return legacy(name, description, Map.of());
    }

    private static McpToolRegistration legacy(String name, String description,
                                               Map<String, Object> annotations) {
        McpToolDescriptor descriptor = new McpToolDescriptor(
                name, null, description,
                Map.of("type", "object"),
                Map.of(),
                annotations);
        McpToolHandler handler = (args, ctx) -> CompletableFuture.completedFuture(
                new McpToolResponse(List.of(), "ok", false));
        return new McpToolRegistration(descriptor, handler);
    }

    private static McpToolHandlerProvider provider(String ref, McpToolHandler handler) {
        return new McpToolHandlerProvider() {
            @Override
            public String handlerRef() {
                return ref;
            }

            @Override
            public McpToolHandler handler() {
                return handler;
            }
        };
    }

    private static McpToolHandlerProvider handler(String ref) {
        return provider(ref, (args, ctx) -> CompletableFuture.completedFuture(
                new McpToolResponse(List.of(), "ok", false)));
    }

    private static McpToolDefinitionSource source(String sourceId, int order,
                                                  McpToolDefinition... definitions) {
        return new McpToolDefinitionSource() {
            @Override
            public String sourceId() {
                return sourceId;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public Collection<McpToolDefinition> load() {
                return List.of(definitions);
            }
        };
    }
}
