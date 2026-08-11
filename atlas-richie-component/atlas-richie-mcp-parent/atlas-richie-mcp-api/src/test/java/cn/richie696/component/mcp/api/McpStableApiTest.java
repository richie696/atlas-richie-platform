package cn.richie696.component.mcp.api;

import cn.richie696.component.mcp.api.annotation.McpArgument;
import cn.richie696.component.mcp.api.annotation.McpTool;
import cn.richie696.component.mcp.api.server.McpToolDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@code atlas-richie-mcp-api} 公共契约（注解、值对象、取消令牌）的稳定性与隔离性：
 * 工具注解 {@code @McpTool} / {@code @McpArgument} 的属性可被反射读取；
 * 取消令牌会抛出统一的 {@link McpCallCancelledException}；{@link McpToolDefinition}
 * 必须对嵌套 schema 做防御性拷贝，避免业务侧持有引用后被外部篡改。
 *
 * @author richie696
 * @since 2026-08-11
 */
class McpStableApiTest {
    @Test
    void exposesToolMetadataWithoutProtocolTypes() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("lookup", String.class);
        McpTool tool = method.getAnnotation(McpTool.class);
        McpArgument argument = method.getParameters()[0].getAnnotation(McpArgument.class);

        assertThat(tool.name()).isEqualTo("customer_lookup");
        assertThat(tool.idempotent()).isTrue();
        assertThat(argument.name()).isEqualTo("customerId");
        assertThat(argument.required()).isTrue();
    }

    @Test
    void cancellationUsesStableBusinessException() {
        McpCancellationToken token = () -> true;

        assertThatThrownBy(token::throwIfCancellationRequested)
                .isInstanceOf(McpCallCancelledException.class)
                .extracting("errorCode")
                .isEqualTo("MCP_CALL_CANCELLED");
    }

    @Test
    void toolDefinitionDefensivelyCopiesNestedValues() {
        List<String> required = new ArrayList<>(List.of("id"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", required);

        McpToolDefinition definition = new McpToolDefinition(
                "customer.lookup", null, null, true, null,
                schema, Map.of(), Map.of(), Set.of(), Duration.ofSeconds(1), null, Map.of());
        required.add("secret");
        schema.put("description", "changed");

        assertThat(definition.inputSchema()).doesNotContainKey("description");
        assertThat(definition.inputSchema().get("required")).isEqualTo(List.of("id"));
        assertThatThrownBy(() -> ((List<Object>) definition.inputSchema().get("required")).add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static final class Fixture {
        @McpTool(name = "customer_lookup", idempotent = true)
        void lookup(@McpArgument(name = "customerId") String customerId) {
        }
    }
}
