package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import cn.richie696.component.mcp.schema.McpCompiledSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 验证 {@link McpResolvedTool} 的不可变 record 语义：
 * registration / inputSchema 必填校验、outputSchema 可空并通过
 * {@link #optionalOutputSchema()} 安全暴露、字段引用原样保留。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpResolvedTool 测试")
class McpResolvedToolTest {

    @Test
    @DisplayName("构造时 null registration 抛 NPE")
    void rejectsNullRegistration() {
        McpCompiledSchema input = mock(McpCompiledSchema.class);
        assertThatThrownBy(() -> new McpResolvedTool(null, input, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("registration");
    }

    @Test
    @DisplayName("构造时 null inputSchema 抛 NPE")
    void rejectsNullInputSchema() {
        McpToolRegistration registration = registration();
        assertThatThrownBy(() -> new McpResolvedTool(registration, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("inputSchema");
    }

    @Test
    @DisplayName("outputSchema 为 null 时 optionalOutputSchema 返回空")
    void optionalOutputSchemaEmptyWhenAbsent() {
        McpResolvedTool resolved = new McpResolvedTool(registration(), mock(McpCompiledSchema.class), null);

        Optional<McpCompiledSchema> result = resolved.optionalOutputSchema();

        assertThat(result).isEmpty();
        assertThat(resolved.outputSchema()).isNull();
    }

    @Test
    @DisplayName("outputSchema 非空时 optionalOutputSchema 返回包含值")
    void optionalOutputSchemaWrapsWhenPresent() {
        McpCompiledSchema output = mock(McpCompiledSchema.class);
        McpResolvedTool resolved = new McpResolvedTool(registration(), mock(McpCompiledSchema.class), output);

        assertThat(resolved.optionalOutputSchema())
                .isPresent()
                .get()
                .isSameAs(output);
    }

    @Test
    @DisplayName("字段引用原样保留")
    void fieldsAreReturnedAsIs() {
        McpToolRegistration registration = registration();
        McpCompiledSchema input = mock(McpCompiledSchema.class);
        McpCompiledSchema output = mock(McpCompiledSchema.class);

        McpResolvedTool resolved = new McpResolvedTool(registration, input, output);

        assertThat(resolved.registration()).isSameAs(registration);
        assertThat(resolved.inputSchema()).isSameAs(input);
        assertThat(resolved.outputSchema()).isSameAs(output);
    }

    private static McpToolRegistration registration() {
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "alpha",
                "Alpha",
                "test",
                Map.of("type", "object", "properties", Map.of()),
                Map.of(),
                Map.of());
        McpToolHandler handler = (a, c) -> CompletableFuture.completedFuture(null);
        return new McpToolRegistration(descriptor, handler);
    }
}