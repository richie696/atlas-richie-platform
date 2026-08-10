package cn.richie696.component.mcp.client.spring.boot;

import cn.richie696.component.mcp.api.McpClientRequest;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.transport.http.McpHttpToolClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class McpDynamicOperationsTest {
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void forwardsRequestLevelHeadersToDiscoveredEndpoint() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/mcp", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                    + "{\"name\":\"dynamic_tool\",\"title\":null,"
                    + "\"description\":\"dynamic\","
                    + "\"inputSchema\":{\"type\":\"object\"},"
                    + "\"outputSchema\":{},\"annotations\":{}}]}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        McpClientProperties properties = new McpClientProperties();
        properties.setNegotiateProtocol(false);
        McpHttpOperations operations = new McpHttpOperations(new McpHttpToolClient(), properties);
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");

        List<McpToolDescriptor> tools = operations.listTools(new McpClientRequest(
                "discovered-server", endpoint, Map.of("Authorization", "Bearer request-token")))
                .toCompletableFuture()
                .join();

        assertThat(tools).extracting(McpToolDescriptor::name).containsExactly("dynamic_tool");
        assertThat(authorization).hasValue("Bearer request-token");
    }
}
