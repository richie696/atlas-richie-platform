package cn.richie696.component.mcp.client.spring.boot;

import cn.richie696.component.mcp.api.McpOperations;
import cn.richie696.component.mcp.transport.http.McpHttpToolClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

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
