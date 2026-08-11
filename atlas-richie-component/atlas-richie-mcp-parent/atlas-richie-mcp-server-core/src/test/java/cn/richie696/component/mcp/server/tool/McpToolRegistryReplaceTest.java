package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对 {@link McpToolRegistry#replace(McpToolRegistration)}、
 * {@link McpToolRegistry#replaceAll(java.util.Collection)}、
 * {@link McpToolRegistry#state()}、
 * {@link McpToolRegistry#removeChangeListener(McpToolRegistryChangeListener)} 等
 * 路径的补充分支覆盖。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolRegistry 补充分支测试")
class McpToolRegistryReplaceTest {

    @Test
    @DisplayName("replace 新注册名 → 描述为 addedTools")
    void replaceNewRegistrationMarksAsAdded() {
        McpToolRegistry registry = new McpToolRegistry();
        AtomicReference<McpToolRefreshResult> change = new AtomicReference<>();
        registry.addChangeListener(change::set);

        McpToolRefreshResult result = registry.replace(registration("alpha"));

        assertThat(result.oldRevision()).isEqualTo(0L);
        assertThat(result.newRevision()).isEqualTo(1L);
        assertThat(result.addedTools()).containsExactly("alpha");
        assertThat(result.removedTools()).isEmpty();
        assertThat(result.updatedTools()).isEmpty();
        assertThat(change.get()).isSameAs(result);
    }

    @Test
    @DisplayName("replace 相同注册 → 返回 unchanged 且不通知监听器")
    void replaceSameRegistrationReturnsUnchanged() {
        McpToolRegistry registry = new McpToolRegistry();
        McpToolRegistration original = registration("alpha");
        registry.register(original);
        CopyOnWriteArrayList<McpToolRefreshResult> changes = new CopyOnWriteArrayList<>();
        registry.addChangeListener(changes::add);

        McpToolRefreshResult result = registry.replace(original);

        assertThat(result.oldRevision()).isEqualTo(1L);
        assertThat(result.newRevision()).isEqualTo(1L);
        assertThat(result.addedTools()).isEmpty();
        assertThat(result.changed()).isFalse();
        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("replace 不同注册替换现有 → 描述为 updatedTools")
    void replaceDifferentRegistrationMarksAsUpdated() {
        McpToolRegistry registry = new McpToolRegistry();
        McpToolRegistration first = registration("alpha");
        registry.register(first);
        AtomicReference<McpToolRefreshResult> change = new AtomicReference<>();
        registry.addChangeListener(change::set);

        McpToolRegistration second = new McpToolRegistration(
                new McpToolDescriptor(
                        "alpha", "alpha-v2", "renamed",
                        Map.of("type", "object", "properties", Map.of()),
                        Map.of(),
                        Map.of("audit", true)),
                (a, c) -> CompletableFuture.completedFuture(response()));

        McpToolRefreshResult result = registry.replace(second);

        assertThat(result.oldRevision()).isEqualTo(1L);
        assertThat(result.newRevision()).isEqualTo(2L);
        assertThat(result.updatedTools()).containsExactly("alpha");
        assertThat(result.addedTools()).isEmpty();
        assertThat(result.removedTools()).isEmpty();
        assertThat(change.get()).isSameAs(result);
    }

    @Test
    @DisplayName("replaceAll 相同候选集合 → 返回 unchanged 且不通知监听器")
    void replaceAllSameSetReturnsUnchanged() {
        McpToolRegistry registry = new McpToolRegistry();
        List<McpToolRegistration> first = List.of(registration("alpha"), registration("beta"));
        registry.replaceAll(first);
        CopyOnWriteArrayList<McpToolRefreshResult> changes = new CopyOnWriteArrayList<>();
        registry.addChangeListener(changes::add);

        McpToolRefreshResult result = registry.replaceAll(first);

        assertThat(result.oldRevision()).isEqualTo(1L);
        assertThat(result.newRevision()).isEqualTo(1L);
        assertThat(result.changed()).isFalse();
        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("replaceAll 候选集合含重名 → 抛 IllegalArgumentException 且不替换")
    void replaceAllWithDuplicateCandidatesFails() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(registration("alpha"));
        long revisionBefore = registry.revision();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registry.replaceAll(List.of(
                registration("beta"),
                registration("beta"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate MCP tool name");
        assertThat(registry.revision()).isEqualTo(revisionBefore);
        assertThat(registry.snapshot(context()).tools())
                .extracting(McpToolDescriptor::name)
                .containsExactly("alpha");
    }

    @Test
    @DisplayName("replaceAll null 入参 → 抛 NPE")
    void replaceAllRejectsNull() {
        McpToolRegistry registry = new McpToolRegistry();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registry.replaceAll(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("registrations");
    }

    @Test
    @DisplayName("state() 暴露当前不可变状态（包含全部 Tool）")
    void stateExposesCurrentSnapshot() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(registration("alpha"));
        registry.register(registration("beta"));

        McpToolRegistryState state = registry.state();

        assertThat(state.revision()).isEqualTo(2L);
        assertThat(state.tools().keySet()).containsExactly("alpha", "beta");
    }

    @Test
    @DisplayName("requireAuthorized 可见 Tool → 返回原始注册项")
    void requireAuthorizedReturnsRegistrationForVisibleTool() {
        McpToolRegistry registry = new McpToolRegistry();
        McpToolRegistration registration = registration("alpha");
        registry.register(registration);

        McpToolRegistration resolved = registry.requireAuthorized("alpha", context());

        assertThat(resolved).isSameAs(registration);
    }

    @Test
    @DisplayName("resolveAuthorized 可见 Tool → 返回 resolvedTool")
    void resolveAuthorizedReturnsResolvedTool() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(registration("alpha"));

        McpResolvedTool resolved = registry.resolveAuthorized("alpha", context());

        assertThat(resolved).isNotNull();
        assertThat(resolved.registration().descriptor().name()).isEqualTo("alpha");
    }

    @Test
    @DisplayName("removeChangeListener 移除已注册监听器")
    void removeChangeListenerUnregistersListener() {
        McpToolRegistry registry = new McpToolRegistry();
        CopyOnWriteArrayList<McpToolRefreshResult> changes = new CopyOnWriteArrayList<>();
        McpToolRegistryChangeListener listener = changes::add;
        registry.addChangeListener(listener);

        registry.removeChangeListener(listener);
        registry.register(registration("alpha"));

        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("addChangeListener 拒绝 null 监听器")
    void addChangeListenerRejectsNull() {
        McpToolRegistry registry = new McpToolRegistry();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registry.addChangeListener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("listener");
    }

    @Test
    @DisplayName("listener 抛异常时主流程仍正常完成")
    void listenerExceptionIsSwallowed() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.addChangeListener(result -> {
            throw new RuntimeException("listener boom");
        });

        long revision = registry.register(registration("alpha"));

        assertThat(revision).isEqualTo(1L);
        assertThat(registry.snapshot(context()).tools())
                .extracting(McpToolDescriptor::name)
                .containsExactly("alpha");
    }

    private static McpToolRegistration registration(String name) {
        return new McpToolRegistration(
                new McpToolDescriptor(
                        name, name, "test",
                        Map.of("type", "object", "properties", Map.of()),
                        Map.of(),
                        Map.of()),
                (McpToolHandler) (a, c) -> CompletableFuture.completedFuture(response()));
    }

    private static McpToolResponse response() {
        return new McpToolResponse(
                List.of(Map.of("type", "text", "text", "ok")),
                Map.of(),
                false);
    }

    private static McpCallContext context() {
        return new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "subject",
                null,
                Map.of(),
                null,
                null);
    }

    @SuppressWarnings("unused")
    private static Set<String> unusedSet() {
        return Set.of();
    }
}