package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolRegistrySnapshot} 的不可变 record 语义：
 * 工具列表被防御性不可变拷贝、字段被原样暴露、版本号透传。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolRegistrySnapshot 测试")
class McpToolRegistrySnapshotTest {

    @Test
    @DisplayName("构造时列表被不可变拷贝（外部突变不可见）")
    void toolsListIsDefensivelyCopied() {
        List<McpToolDescriptor> mutable = new ArrayList<>();
        mutable.add(descriptor("alpha"));

        McpToolRegistrySnapshot snapshot = new McpToolRegistrySnapshot(1L, mutable);

        mutable.clear();
        mutable.add(descriptor("beta"));

        assertThat(snapshot.tools())
                .extracting(McpToolDescriptor::name)
                .containsExactly("alpha");
    }

    @Test
    @DisplayName("字段被原样暴露")
    void exposesRevisionAndTools() {
        List<McpToolDescriptor> tools = List.of(descriptor("alpha"), descriptor("beta"));

        McpToolRegistrySnapshot snapshot = new McpToolRegistrySnapshot(99L, tools);

        assertThat(snapshot.revision()).isEqualTo(99L);
        assertThat(snapshot.tools())
                .extracting(McpToolDescriptor::name)
                .containsExactly("alpha", "beta");
    }

    @Test
    @DisplayName("相等语义：相同字段 equals 为 true 且 hashCode 一致")
    void equalSnapshotsAreEqual() {
        List<McpToolDescriptor> tools = List.of(descriptor("alpha"));

        McpToolRegistrySnapshot left = new McpToolRegistrySnapshot(7L, tools);
        McpToolRegistrySnapshot right = new McpToolRegistrySnapshot(7L, tools);

        assertThat(left).isEqualTo(right);
        assertThat(left.hashCode()).isEqualTo(right.hashCode());
    }

    @Test
    @DisplayName("不等语义：版本号不同则对象不等")
    void differentRevisionsAreNotEqual() {
        McpToolRegistrySnapshot left = new McpToolRegistrySnapshot(1L, List.of());
        McpToolRegistrySnapshot right = new McpToolRegistrySnapshot(2L, List.of());

        assertThat(left).isNotEqualTo(right);
    }

    private static McpToolDescriptor descriptor(String name) {
        return new McpToolDescriptor(
                name, name, "test",
                Map.of("type", "object", "properties", Map.of()),
                Map.of(),
                Map.of());
    }
}