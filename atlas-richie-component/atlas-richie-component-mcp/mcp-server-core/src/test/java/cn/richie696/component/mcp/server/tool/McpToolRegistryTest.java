package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolRegistryTest {
    @Test
    void returnsDeterministicallySortedToolsAndTracksRevision() {
        McpToolRegistry registry = new McpToolRegistry();
        assertThat(registry.register(registration("zeta.tool"))).isEqualTo(1);
        assertThat(registry.register(registration("alpha_tool"))).isEqualTo(2);

        McpToolRegistrySnapshot snapshot = registry.snapshot(context("subject"));

        assertThat(snapshot.revision()).isEqualTo(2);
        assertThat(snapshot.tools()).extracting(McpToolDescriptor::name)
                .containsExactly("alpha_tool", "zeta.tool");
        assertThat(registry.unregister("alpha_tool")).isEqualTo(3);
        assertThat(registry.unregister("missing")).isEqualTo(3);
    }

    @Test
    void rejectsDuplicateAndInvalidToolDefinitionsAtRegistrationTime() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(registration("customer.lookup"));

        assertThatThrownBy(() -> registry.register(registration("customer.lookup")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> registry.register(registration("contains space")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
        assertThatThrownBy(() -> registry.register(new McpToolRegistration(
                descriptor("invalid-schema", Map.of("type", "string")),
                (arguments, context) -> CompletableFuture.completedFuture(response()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("root type must be object");
    }

    @Test
    void authorizationFiltersListAndDoesNotRevealHiddenToolOnLookup() {
        McpToolRegistry registry = new McpToolRegistry(
                (descriptor, context) -> descriptor.name().startsWith(context.subject()));
        registry.register(registration("alice.lookup"));
        registry.register(registration("bob.lookup"));

        assertThat(registry.snapshot(context("alice")).tools())
                .extracting(McpToolDescriptor::name)
                .containsExactly("alice.lookup");
        assertThatThrownBy(() -> registry.requireAuthorized("bob.lookup", context("alice")))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32602);
                    assertThat(exception.getMessage()).isEqualTo("Unknown tool: bob.lookup");
                });
    }

    @Test
    void exposesStableBusinessHandlerWithoutProtocolSdkTypes() {
        McpToolRegistration registration = registration("customer.lookup");

        McpToolResponse result = registration.handler()
                .handle(Map.of("id", "C-1"), context("alice"))
                .toCompletableFuture()
                .join();

        assertThat(result.content()).containsExactly(Map.of("type", "text", "text", "ok"));
    }

    private McpToolRegistration registration(String name) {
        return new McpToolRegistration(
                descriptor(name, Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of())),
                (arguments, context) -> CompletableFuture.completedFuture(response()));
    }

    private McpToolDescriptor descriptor(String name, Map<String, Object> inputSchema) {
        return new McpToolDescriptor(
                name,
                name,
                "test",
                inputSchema,
                Map.of(),
                Map.of());
    }

    private McpToolResponse response() {
        return new McpToolResponse(
                List.of(Map.of("type", "text", "text", "ok")),
                Map.of(),
                false);
    }

    private McpCallContext context(String subject) {
        return new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                subject,
                null,
                Map.of(),
                null,
                null);
    }
}
