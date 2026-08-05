package cn.richie696.component.mcp.api;

import cn.richie696.component.mcp.api.annotation.McpArgument;
import cn.richie696.component.mcp.api.annotation.McpTool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static final class Fixture {
        @McpTool(name = "customer_lookup", idempotent = true)
        void lookup(@McpArgument(name = "customerId") String customerId) {
        }
    }
}
