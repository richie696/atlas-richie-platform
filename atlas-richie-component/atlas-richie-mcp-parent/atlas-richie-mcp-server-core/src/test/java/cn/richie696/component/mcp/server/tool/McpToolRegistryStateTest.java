package cn.richie696.component.mcp.server.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpToolRegistryState} 的不可变 record 语义：
 * 入参 tools 被拷贝为 TreeMap + unmodifiable 包装、字段被原样暴露、
 * 包装后的 map 不允许修改。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolRegistryState 测试")
class McpToolRegistryStateTest {

    @Test
    @DisplayName("构造时入参 NavigableMap 被复制为不可变 NavigableMap（外部突变不可见）")
    void toolsMapIsCopiedAndUnmodifiable() {
        NavigableMap<String, McpResolvedTool> mutable = new TreeMap<>();
        mutable.put("alpha", resolved("alpha"));

        McpToolRegistryState state = new McpToolRegistryState(1L, mutable);

        mutable.put("beta", resolved("beta"));

        assertThat(state.tools())
                .containsOnlyKeys("alpha")
                .doesNotContainKey("beta");
        assertThatThrownBy(() -> state.tools().put("x", resolved("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("构造时入参 NavigableMap 被复制为 TreeMap（保持字典序）")
    void toolsAreSortedNaturally() {
        NavigableMap<String, McpResolvedTool> ordered = new TreeMap<>();
        ordered.put("zeta", resolved("zeta"));
        ordered.put("alpha", resolved("alpha"));

        McpToolRegistryState state = new McpToolRegistryState(0L, ordered);

        assertThat(state.tools().keySet()).containsExactly("alpha", "zeta");
    }

    @Test
    @DisplayName("字段被原样暴露")
    void exposesRevisionAndTools() {
        NavigableMap<String, McpResolvedTool> tools = new TreeMap<>();
        tools.put("alpha", resolved("alpha"));

        McpToolRegistryState state = new McpToolRegistryState(99L, tools);

        assertThat(state.revision()).isEqualTo(99L);
        assertThat(state.tools()).containsKey("alpha");
    }

    @Test
    @DisplayName("相等语义：相同字段 equals 为 true 且 hashCode 一致")
    void equalStatesAreEqual() {
        NavigableMap<String, McpResolvedTool> tools = new TreeMap<>();
        tools.put("alpha", resolved("alpha"));

        McpToolRegistryState left = new McpToolRegistryState(7L, tools);
        McpToolRegistryState right = new McpToolRegistryState(7L, tools);

        assertThat(left).isEqualTo(right);
        assertThat(left.hashCode()).isEqualTo(right.hashCode());
    }

    private static McpResolvedTool resolved(String name) {
        McpToolRegistration registration = new McpToolRegistration(
                new cn.richie696.component.mcp.api.model.McpToolDescriptor(
                        name, name, "test",
                        Map.of("type", "object", "properties", Map.of()),
                        Map.of(),
                        Map.of()),
                (a, c) -> java.util.concurrent.CompletableFuture.completedFuture(null));
        cn.richie696.component.mcp.schema.McpCompiledSchema schema =
                instance -> cn.richie696.component.mcp.schema.McpSchemaValidationResult.valid();
        return new McpResolvedTool(registration, schema, null);
    }
}