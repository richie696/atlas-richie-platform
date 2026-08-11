package cn.richie696.component.mcp.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpToolDescriptor} 紧凑构造器对 {@code name} 的必填校验，
 * 以及对嵌套 Map/List/Set 的递归不可变包装。
 */
@DisplayName("McpToolDescriptor Tool 描述 record")
class McpToolDescriptorTest {

    @Test
    @DisplayName("完整参数：访问器返回原值")
    void shouldExposeAllFields() {
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "customer.lookup", "Lookup", "Find a customer",
                Map.of("type", "object"),
                Map.of("type", "object"),
                Map.of("audience", "internal"));

        assertThat(descriptor.name()).isEqualTo("customer.lookup");
        assertThat(descriptor.title()).isEqualTo("Lookup");
        assertThat(descriptor.description()).isEqualTo("Find a customer");
        assertThat(descriptor.inputSchema()).containsEntry("type", "object");
        assertThat(descriptor.outputSchema()).containsEntry("type", "object");
        assertThat(descriptor.annotations()).containsEntry("audience", "internal");
    }

    @Test
    @DisplayName("null name 抛 NullPointerException")
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new McpToolDescriptor(null, "t", "d", null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("null/空 schema 回落为不可变空 Map")
    void shouldFallbackEmptySchemas() {
        McpToolDescriptor descriptor = new McpToolDescriptor("n", null, null, null, null, null);

        assertThat(descriptor.inputSchema()).isEmpty();
        assertThat(descriptor.outputSchema()).isEmpty();
        assertThat(descriptor.annotations()).isEmpty();
        assertThat(descriptor.inputSchema()).isUnmodifiable();
    }

    @Nested
    @DisplayName("不可变包装：递归保护嵌套容器")
    class ImmutableWrapping {

        @Test
        @DisplayName("外部修改 source Map 不影响 descriptor 内部状态")
        void shouldNotLeakThroughSourceMutation() {
            List<String> required = new ArrayList<>(List.of("id"));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("required", required);

            McpToolDescriptor descriptor = new McpToolDescriptor("n", null, null, schema, null, null);

            required.add("secret");
            schema.put("sneaky", "value");

            assertThat(descriptor.inputSchema()).doesNotContainKey("sneaky");
            @SuppressWarnings("unchecked")
            List<String> requiredAfter = (List<String>) descriptor.inputSchema().get("required");
            assertThat(requiredAfter).containsExactly("id");
        }

        @Test
        @DisplayName("Map 内部嵌套的 List/Set/Map 都被不可变包装")
        void shouldRecursivelyImmutableNestedContainers() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("k", "v");
            List<Object> list = new ArrayList<>(List.of("a", inner, Set.of(1, 2)));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("items", list);

            McpToolDescriptor descriptor = new McpToolDescriptor("n", null, null, schema, null, null);

            Object items = descriptor.inputSchema().get("items");
            assertThat(items).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<Object> immutableList = (List<Object>) items;
            assertThatThrownBy(() -> immutableList.add("new"))
                    .isInstanceOf(UnsupportedOperationException.class);

            // Map 内层也被不可变
            Object nestedMap = immutableList.get(1);
            assertThat(nestedMap).isInstanceOf(Map.class);
            assertThatThrownBy(() -> ((Map<Object, Object>) nestedMap).put("x", "y"))
                    .isInstanceOf(UnsupportedOperationException.class);

            // Set 也被不可变
            Object setValue = immutableList.get(2);
            assertThatThrownBy(() -> ((Set<Object>) setValue).add(99))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("非容器原值（如 String/Integer）原样保留")
        void shouldPreserveScalarValues() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("title", "Customer");
            schema.put("count", 42);
            schema.put("active", true);

            McpToolDescriptor descriptor = new McpToolDescriptor("n", null, null, schema, null, null);

            assertThat(descriptor.inputSchema())
                    .containsEntry("title", "Customer")
                    .containsEntry("count", 42)
                    .containsEntry("active", true);
        }
    }
}
