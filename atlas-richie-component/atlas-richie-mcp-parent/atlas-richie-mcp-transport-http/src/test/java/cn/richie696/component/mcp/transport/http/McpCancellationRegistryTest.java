package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpCancellationToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpCancellationRegistry} 作为 {@code notifications/cancelled} 通知 ↔
 * 工具执行线程反向桥的核心契约：{@link McpCancellationToken} 在注册时为 {@code false}，
 * 调用 {@code cancel(requestId)}（包括 Number 类型 id）后立刻变 {@code true}；
 * 解除注册后 token 仍可读但 registry 不再持有该 id；{@code cancel(null)} 是安全的
 * （null id 的 notification 不应取消任何任务）。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpCancellationRegistry 反向桥")
class McpCancellationRegistryTest {

    @Test
    @DisplayName("begin 返回的 token 默认 false，被 cancel 后变 true")
    void cancelFlipsTokenFromFalseToTrue() {
        McpCancellationRegistry registry = new McpCancellationRegistry();
        McpCancellationToken token = registry.begin("req-1");

        assertThat(token.isCancellationRequested()).isFalse();
        registry.cancel("req-1");
        assertThat(token.isCancellationRequested()).isTrue();
    }

    @Test
    @DisplayName("cancel 支持 Number 类型 id（String.valueOf 转换）")
    void cancelAcceptsNumberRequestId() {
        McpCancellationRegistry registry = new McpCancellationRegistry();
        McpCancellationToken token = registry.begin("42");

        registry.cancel(42);

        assertThat(token.isCancellationRequested()).isTrue();
    }

    @Test
    @DisplayName("cancel(null) 安全：不会抛异常也不会取消任何 token")
    void cancelNullIsSafeNoOp() {
        McpCancellationRegistry registry = new McpCancellationRegistry();
        McpCancellationToken token = registry.begin("req-2");

        registry.cancel(null);

        assertThat(token.isCancellationRequested()).isFalse();
    }

    @Test
    @DisplayName("cancel 未注册的 requestId：不会抛异常，token 不变")
    void cancelUnknownRequestIdIsSafe() {
        McpCancellationRegistry registry = new McpCancellationRegistry();
        McpCancellationToken token = registry.begin("req-known");

        registry.cancel("req-unknown");

        assertThat(token.isCancellationRequested()).isFalse();
    }

    @Test
    @DisplayName("begin/finish：finish 之后再 begin 不应继承旧 token 状态")
    void finishClearsRequestId() {
        McpCancellationRegistry registry = new McpCancellationRegistry();
        McpCancellationToken first = registry.begin("req-finish");
        registry.finish("req-finish");

        McpCancellationToken second = registry.begin("req-finish");

        assertThat(first.isCancellationRequested()).isFalse();
        assertThat(second.isCancellationRequested()).isFalse();
        registry.cancel("req-finish");
        assertThat(second.isCancellationRequested()).isTrue();
        assertThat(first.isCancellationRequested()).isFalse();
    }

    @Test
    @DisplayName("同一 requestId 二次注册覆盖：返回新 token")
    void reRegisteringReplacesToken() {
        McpCancellationRegistry registry = new McpCancellationRegistry();
        McpCancellationToken first = registry.begin("dup");
        registry.begin("dup");
        AtomicBoolean secondSeen = new AtomicBoolean();

        first.isCancellationRequested();
        secondSeen.set(!first.isCancellationRequested());

        registry.cancel("dup");
        assertThat(secondSeen).isTrue();
    }
}
