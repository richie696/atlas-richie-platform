package cn.richie696.component.mcp.transport.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpRemoteTool} 在紧凑构造器里的不可变包裹：三个 schema 字段
 * （{@code inputSchema} / {@code outputSchema} / {@code annotations}）在 null 时归一为
 * {@link Map#of()}；非 null 时做防御性拷贝，避免外部对入参 Map 的二次修改污染本对象。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpRemoteTool 远端工具元数据")
class McpRemoteToolTest {

    @Test
    @DisplayName("三个 schema 字段都为 null 时归一为空 Map")
    void allNullSchemasNormalizedToEmptyMaps() {
        McpRemoteTool tool = new McpRemoteTool("echo", "Echo", "Echo input", null, null, null);

        assertThat(tool.inputSchema()).isEmpty();
        assertThat(tool.outputSchema()).isEmpty();
        assertThat(tool.annotations()).isEmpty();
    }

    @Test
    @DisplayName("外部 Map 修改不影响构造后的 schema 快照")
    void outerSchemaMutationDoesNotAffectSnapshot() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        McpRemoteTool tool = new McpRemoteTool(
                "echo", "Echo", "Echo input", schema, null, null);

        schema.put("late", "value");

        assertThat(tool.inputSchema()).containsOnlyKeys("type");
    }

    @Test
    @DisplayName("schema 不可变：写入抛出 UnsupportedOperationException")
    void schemaViewsAreUnmodifiable() {
        McpRemoteTool tool = new McpRemoteTool(
                "echo", "Echo", "Echo input",
                Map.of("type", "object"),
                Map.of(),
                Map.of("destructiveHint", true));

        assertThatThrownBy(() -> tool.inputSchema().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> tool.annotations().put("y", 2))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("合法输入：访问器返回原值")
    void accessorsReturnOriginalValues() {
        McpRemoteTool tool = new McpRemoteTool(
                "lookup", "Lookup", "Look up tenant",
                Map.of("type", "object"),
                Map.of("type", "object"),
                Map.of("readOnlyHint", true));

        assertThat(tool.name()).isEqualTo("lookup");
        assertThat(tool.title()).isEqualTo("Lookup");
        assertThat(tool.description()).isEqualTo("Look up tenant");
        assertThat(tool.inputSchema()).containsEntry("type", "object");
        assertThat(tool.outputSchema()).containsEntry("type", "object");
        assertThat(tool.annotations()).containsEntry("readOnlyHint", true);
    }
}
