package cn.richie696.component.mcp.client.spring.boot;

import cn.richie696.component.mcp.api.McpOperations;
import cn.richie696.component.mcp.api.McpDynamicOperations;
import cn.richie696.component.mcp.transport.http.McpHttpToolClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 MCP 客户端 Spring Boot 自动装配：{@code platform.component.mcp.client.servers.*} 配置
 * 会正确绑定到 {@link McpClientProperties}，并按需注册 {@link McpHttpToolClient}、
 * {@link McpOperations} 与 {@link McpDynamicOperations}；同时，确认 {@code enabled=false}
 * 可整体关闭客户端装配，避免空上下文场景下的误注入。
 *
 * @author richie696
 * @since 2026-08-11
 */
class McpClientAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    McpClientAutoConfiguration.class));

    @Test
    void bindsServerPropertiesAndCreatesOperations() {
        contextRunner
                .withPropertyValues(
                        "platform.component.mcp.client.servers.demo.endpoint=https://example.test/mcp",
                        "platform.component.mcp.client.servers.demo.headers.x-tenant-id=tenant-1")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpHttpToolClient.class);
                    assertThat(context).hasSingleBean(McpOperations.class);
                    assertThat(context).hasSingleBean(McpDynamicOperations.class);
                    McpClientProperties properties = context.getBean(McpClientProperties.class);
                    assertThat(properties.getServers()).containsKey("demo");
                    assertThat(properties.getServers().get("demo").getHeaders())
                            .containsEntry("x-tenant-id", "tenant-1");
                });
    }

    @Test
    void disabledPropertySkipsClientBeans() {
        contextRunner
                .withPropertyValues("platform.component.mcp.client.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(McpOperations.class));
    }
}
