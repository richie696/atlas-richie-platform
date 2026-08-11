package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpToolDefinition} 紧凑构造器对 Tool 名称正则、超时正数与集合不可变包装。
 */
@DisplayName("McpToolDefinition 工具合并定义 record")
class McpToolDefinitionTest {

    @Test
    @DisplayName("完整参数：暴露全部字段并对 Map/Set 做不可变拷贝")
    void shouldExposeAllFields() {
        McpToolDefinition definition = new McpToolDefinition(
                "customer.lookup", "Lookup", "desc", true, "default",
                Map.of("type", "object"),
                Map.of("type", "object"),
                Map.of("audience", "internal"),
                Set.of("customer:read"),
                Duration.ofSeconds(2),
                "customer",
                Map.of("audit", true));

        assertThat(definition.name()).isEqualTo("customer.lookup");
        assertThat(definition.title()).isEqualTo("Lookup");
        assertThat(definition.enabled()).isTrue();
        assertThat(definition.handlerRef()).isEqualTo("default");
        assertThat(definition.requiredScopes()).containsExactly("customer:read");
        assertThat(definition.timeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(definition.group()).isEqualTo("customer");
        assertThat(definition.policies()).containsEntry("audit", true);
    }

    @Test
    @DisplayName("null/空 name 抛 NullPointerException")
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new McpToolDefinition(
                null, "t", "d", true, null, null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Nested
    @DisplayName("Tool 名正则校验")
    class NamePatternValidation {

        @Test
        @DisplayName("合法字符：字母数字 + _ . - 长度 1-128")
        void shouldAcceptAllowedCharacters() {
            assertValid("a");
            assertValid("a.b-c_d");
            assertValid("1");
            assertValid("A".repeat(128));
        }

        @Test
        @DisplayName("非法字符或越界长度抛 IllegalArgumentException")
        void shouldRejectInvalidNames() {
            assertInvalid("");
            assertInvalid("a b");
            assertInvalid("a/b");
            assertInvalid("a:b");
            assertInvalid("a".repeat(129));
            assertInvalid("中文");
        }

        private void assertValid(String name) {
            new McpToolDefinition(name, null, null, true, null, null, null, null, null, null, null, null);
        }

        private void assertInvalid(String name) {
            assertThatThrownBy(() -> new McpToolDefinition(
                    name, null, null, true, null, null, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name must match");
        }
    }

    @Nested
    @DisplayName("timeout 校验")
    class TimeoutValidation {

        @Test
        @DisplayName("timeout=0 抛 IllegalArgumentException")
        void shouldRejectZeroTimeout() {
            assertThatThrownBy(() -> new McpToolDefinition(
                    "n", null, null, true, null, null, null, null, null,
                    Duration.ZERO, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be positive");
        }

        @Test
        @DisplayName("timeout<0 抛 IllegalArgumentException")
        void shouldRejectNegativeTimeout() {
            assertThatThrownBy(() -> new McpToolDefinition(
                    "n", null, null, true, null, null, null, null, null,
                    Duration.ofMillis(-1), null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be positive");
        }

        @Test
        @DisplayName("timeout=null 被允许（表示未配置）")
        void shouldAcceptNullTimeout() {
            McpToolDefinition def = new McpToolDefinition(
                    "n", null, null, true, null, null, null, null, null, null, null, null);
            assertThat(def.timeout()).isNull();
        }
    }

    @Test
    @DisplayName("空/空白 group 回落为 null")
    void shouldFallbackBlankGroup() {
        assertThat(new McpToolDefinition(
                "n", null, null, true, null, null, null, null, null, null, "  ", null).group()).isNull();
        assertThat(new McpToolDefinition(
                "n", null, null, true, null, null, null, null, null, null, "", null).group()).isNull();
    }

    @Test
    @DisplayName("空/空白 handlerRef 回落为 null")
    void shouldFallbackBlankHandlerRef() {
        assertThat(new McpToolDefinition(
                "n", null, null, true, " ", null, null, null, null, null, null, null).handlerRef()).isNull();
        assertThat(new McpToolDefinition(
                "n", null, null, true, "", null, null, null, null, null, null, null).handlerRef()).isNull();
    }

    @Test
    @DisplayName("Schema 递归不可变：嵌套 List/Map 修改抛异常")
    void shouldRecursivelyImmutableNestedValues() {
        java.util.ArrayList<Object> mutableList = new java.util.ArrayList<>(List.of("a"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("items", mutableList);

        McpToolDefinition def = new McpToolDefinition(
                "n", null, null, true, null, schema, null, null, null, null, null, null);

        mutableList.add("b");
        Object items = def.inputSchema().get("items");
        assertThat(items).isInstanceOf(List.class);
        assertThatThrownBy(() -> ((List<Object>) items).add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("requiredScopes/Map 字段在 null 时回落到不可变空集合")
    void shouldFallbackEmptyCollections() {
        McpToolDefinition def = new McpToolDefinition(
                "n", null, null, true, null, null, null, null, null, null, null, null);

        assertThat(def.requiredScopes()).isEmpty();
        assertThat(def.inputSchema()).isEmpty();
        assertThat(def.outputSchema()).isEmpty();
        assertThat(def.annotations()).isEmpty();
        assertThat(def.policies()).isEmpty();
    }
}
