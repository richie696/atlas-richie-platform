package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import cn.richie696.component.mcp.transport.http.McpServerHttpEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    McpServerAutoConfiguration.class));

    @Test
    void registersToolBeansAndCreatesEndpoint() {
        contextRunner
                .withBean(McpToolRegistration.class, this::toolRegistration)
                .run(context -> {
                    assertThat(context).hasSingleBean(McpToolRegistry.class);
                    assertThat(context).hasSingleBean(McpServerHttpEndpoint.class);
                    assertThat(context.getBean(McpToolRegistry.class).revision()).isEqualTo(1);
                });
    }

    @Test
    void disabledPropertySkipsServerBeans() {
        contextRunner
                .withPropertyValues("platform.component.mcp.server.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(McpToolRegistry.class));
    }

    @Test
    void exposesProtectedResourceMetadataWhenOAuthIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "platform.component.mcp.server.oauth.enabled=true",
                        "platform.component.mcp.server.oauth.resource=https://mcp.example/mcp",
                        "platform.component.mcp.server.oauth.authorization-servers[0]=https://idp.example",
                        "platform.component.mcp.server.oauth.scopes-supported[0]=tools.read")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpOAuthMetadataController.class);
                    assertThat(context.getBean(McpOAuthMetadataController.class).get())
                            .containsEntry("resource", "https://mcp.example/mcp")
                            .containsEntry("authorization_servers", java.util.List.of("https://idp.example"));
                });
    }

    private McpToolRegistration toolRegistration() {
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "echo",
                null,
                "Echo input",
                Map.of("type", "object"),
                Map.of(),
                Map.of());
        McpToolHandler handler = (arguments, callContext) ->
                CompletableFuture.completedFuture(new McpToolResponse(
                        java.util.List.of(Map.of("type", "text", "text", "ok")),
                        Map.of(),
                        false));
        return new McpToolRegistration(descriptor, handler);
    }
}
