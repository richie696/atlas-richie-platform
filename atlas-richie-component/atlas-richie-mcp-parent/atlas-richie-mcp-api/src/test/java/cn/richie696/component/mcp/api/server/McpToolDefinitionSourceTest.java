package cn.richie696.component.mcp.api.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolDefinitionSource} 与 {@link McpToolHandlerProvider} 的 SPI 契约：
 * sourceId 唯一标识、order() 默认 0；Provider 通过 handlerRef() 被 YAML 引用。
 */
@DisplayName("McpToolDefinitionSource / McpToolHandlerProvider SPI 扩展点")
class McpToolDefinitionSourceTest {

    @Test
    @DisplayName("McpToolDefinitionSource：sourceId() 与 order() 默认 0")
    void shouldExposeSourceIdAndDefaultOrder() {
        McpToolDefinitionSource source = new McpToolDefinitionSource() {
            @Override
            public String sourceId() {
                return "annotation-scanner";
            }

            @Override
            public Collection<McpToolDefinition> load() {
                return List.of();
            }
        };

        assertThat(source.sourceId()).isEqualTo("annotation-scanner");
        assertThat(source.order()).isEqualTo(0);
    }

    @Test
    @DisplayName("McpToolDefinitionSource：覆盖 order() 自定义排序")
    void shouldAllowCustomOrder() {
        McpToolDefinitionSource source = new McpToolDefinitionSource() {
            @Override
            public String sourceId() {
                return "nacos";
            }

            @Override
            public int order() {
                return 100;
            }

            @Override
            public Collection<McpToolDefinition> load() {
                return List.of();
            }
        };

        assertThat(source.order()).isEqualTo(100);
    }

    @Test
    @DisplayName("McpToolDefinitionSource.load() 可重复调用且无副作用")
    void loadShouldBeIdempotent() {
        AtomicInteger counter = new AtomicInteger();
        McpToolDefinitionSource source = new McpToolDefinitionSource() {
            @Override
            public String sourceId() {
                return "scanner";
            }

            @Override
            public Collection<McpToolDefinition> load() {
                counter.incrementAndGet();
                return List.of(build("a"), build("b"));
            }
        };

        Collection<McpToolDefinition> first = source.load();
        Collection<McpToolDefinition> second = source.load();

        assertThat(counter.get()).isEqualTo(2);
        assertThat(first).hasSize(2);
        assertThat(second).hasSize(2);
    }

    @Test
    @DisplayName("McpToolHandlerProvider：handlerRef() 与 handler() 必须非 null")
    void handlerProviderShouldExposeHandler() {
        McpToolHandler handler = (args, ctx) -> CompletableFuture.completedFuture(
                new cn.richie696.component.mcp.api.model.McpToolResponse(
                        List.of(Map.of("text", "ok")), null, false));
        McpToolHandlerProvider provider = new McpToolHandlerProvider() {
            @Override
            public String handlerRef() {
                return "customer-lookup-handler";
            }

            @Override
            public McpToolHandler handler() {
                return handler;
            }
        };

        assertThat(provider.handlerRef()).isEqualTo("customer-lookup-handler");
        assertThat(provider.handler()).isSameAs(handler);
    }

    @Test
    @DisplayName("McpToolHandlerProvider 允许多实例共存（不同 ref）")
    void shouldAllowMultipleProviders() {
        McpToolHandlerProvider a = new McpToolHandlerProvider() {
            @Override
            public String handlerRef() {
                return "a";
            }

            @Override
            public McpToolHandler handler() {
                return (args, ctx) -> CompletableFuture.completedFuture(
                        new cn.richie696.component.mcp.api.model.McpToolResponse(
                                List.of(), null, false));
            }
        };
        McpToolHandlerProvider b = new McpToolHandlerProvider() {
            @Override
            public String handlerRef() {
                return "b";
            }

            @Override
            public McpToolHandler handler() {
                return a.handler();
            }
        };

        assertThat(a.handlerRef()).isNotEqualTo(b.handlerRef());
        assertThat(a.handlerRef()).isEqualTo("a");
        assertThat(b.handlerRef()).isEqualTo("b");
    }

    private static McpToolDefinition build(String suffix) {
        return new McpToolDefinition(
                "tool." + suffix, null, null, true, null, null, null, null,
                Set.of(), Duration.ofSeconds(1), null, Map.of());
    }
}
