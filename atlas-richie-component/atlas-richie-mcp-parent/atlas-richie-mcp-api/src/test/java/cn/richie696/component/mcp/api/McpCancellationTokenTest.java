package cn.richie696.component.mcp.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpCancellationToken} 的 NONE 单例与 default 方法，
 * 以及 {@link McpCallCancelledException} 的统一错误码行为。
 */
@DisplayName("McpCancellationToken 协作式取消")
class McpCancellationTokenTest {

    @Test
    @DisplayName("NONE 单例：永远不报告取消")
    void noneTokenShouldNeverReportCancellation() {
        assertThat(McpCancellationToken.NONE.isCancellationRequested()).isFalse();
    }

    @Test
    @DisplayName("throwIfCancellationRequested 在取消时抛 McpCallCancelledException")
    void shouldThrowWhenCancelled() {
        McpCancellationToken token = () -> true;

        assertThatThrownBy(token::throwIfCancellationRequested)
                .isInstanceOf(McpCallCancelledException.class)
                .extracting("errorCode")
                .isEqualTo("MCP_CALL_CANCELLED");
    }

    @Test
    @DisplayName("throwIfCancellationRequested 在未取消时不抛异常")
    void shouldNotThrowWhenNotCancelled() {
        McpCancellationToken token = () -> false;
        token.throwIfCancellationRequested();
    }

    @Test
    @DisplayName("lambda 形式：捕获每次调用结果")
    void lambdaShouldBeEvaluatedEachCall() {
        AtomicReference<Boolean> cancelled = new AtomicReference<>(false);
        McpCancellationToken token = cancelled::get;

        assertThat(token.isCancellationRequested()).isFalse();
        cancelled.set(true);
        assertThat(token.isCancellationRequested()).isTrue();
    }

    @Test
    @DisplayName("可与 CompletionStage 配合模拟取消传播")
    void shouldComposeWithAsyncStages() {
        McpCancellationToken token = () -> true;
        CompletionStage<String> stage = CompletableFuture.completedFuture("ok")
                .thenApply(value -> {
                    token.throwIfCancellationRequested();
                    return value;
                });

        assertThatThrownBy(() -> stage.toCompletableFuture().join())
                .isInstanceOf(java.util.concurrent.CompletionException.class)
                .hasCauseInstanceOf(McpCallCancelledException.class);
    }
}
