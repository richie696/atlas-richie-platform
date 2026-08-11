package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpToolRegistry} 的注册 / 快照 / 鉴权 / 修订号：注册返回单调递增的修订号；
 * 重复注册 / 非法工具名 / 非 object 根 schema 必须拒绝；visibility policy 与
 * requiredScopes 在 list / lookup 路径上保持一致；{@code replaceAll} 一次只发一个
 * 变更事件，整体失败时旧版本原样保留；并发读写场景下 reader 只能看到完整快照。
 *
 * @author richie696
 * @since 2026-08-11
 */
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
    void requiredScopesAreAppliedToListAndLookup() {
        McpToolRegistry registry = new McpToolRegistry(
                new McpRequiredScopeVisibilityPolicy(McpToolVisibilityPolicy.ALLOW_ALL));
        McpToolRegistration base = registration("orders.read");
        McpToolDescriptor descriptor = base.descriptor();
        registry.register(new McpToolRegistration(new McpToolDescriptor(
                descriptor.name(), descriptor.title(), descriptor.description(),
                descriptor.inputSchema(), descriptor.outputSchema(),
                Map.of("requiredScopes", List.of("orders:read"))), base.handler()));

        assertThat(registry.snapshot(context("alice")).tools()).isEmpty();
        McpCallContext authorized = new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of("scopes", List.of("orders:read", "profile")),
                null,
                null);
        assertThat(registry.snapshot(authorized).tools())
                .extracting(McpToolDescriptor::name)
                .containsExactly("orders.read");
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

    @Test
    void replaceAllPublishesOneAtomicChangeAndKeepsOldStateOnFailure() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.replaceAll(List.of(registration("alpha"), registration("beta")));
        ConcurrentLinkedQueue<McpToolRefreshResult> changes = new ConcurrentLinkedQueue<>();
        registry.addChangeListener(changes::add);

        McpToolRefreshResult result = registry.replaceAll(
                List.of(registration("beta"), registration("gamma")));

        assertThat(result.oldRevision()).isEqualTo(1);
        assertThat(result.newRevision()).isEqualTo(2);
        assertThat(result.addedTools()).containsExactly("gamma");
        assertThat(result.removedTools()).containsExactly("alpha");
        assertThat(changes).containsExactly(result);

        assertThatThrownBy(() -> registry.replaceAll(List.of(
                registration("valid"),
                new McpToolRegistration(
                        descriptor("invalid", Map.of("type", "string")),
                        (arguments, context) -> CompletableFuture.completedFuture(response())))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.revision()).isEqualTo(2);
        assertThat(registry.snapshot(context("subject")).tools())
                .extracting(McpToolDescriptor::name)
                .containsExactly("beta", "gamma");
    }

    @Test
    void concurrentReadersOnlyObserveCompleteSnapshots() throws Exception {
        McpToolRegistry registry = new McpToolRegistry();
        List<McpToolRegistration> versionA = List.of(registration("a.one"), registration("a.two"));
        List<McpToolRegistration> versionB = List.of(registration("b.one"), registration("b.two"));
        registry.replaceAll(versionA);
        Set<List<String>> allowed = Set.of(
                List.of("a.one", "a.two"),
                List.of("b.one", "b.two"));
        ConcurrentLinkedQueue<List<String>> invalid = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int reader = 0; reader < 100; reader++) {
                executor.submit(() -> {
                    start.await();
                    for (int iteration = 0; iteration < 100; iteration++) {
                        List<String> names = registry.snapshot(context("subject")).tools().stream()
                                .map(McpToolDescriptor::name)
                                .toList();
                        if (!allowed.contains(names)) invalid.add(names);
                    }
                    return null;
                });
            }
            executor.submit(() -> {
                start.await();
                for (int iteration = 0; iteration < 100; iteration++) {
                    registry.replaceAll((iteration & 1) == 0 ? versionB : versionA);
                }
                return null;
            });
            start.countDown();
        }

        assertThat(invalid).isEmpty();
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
