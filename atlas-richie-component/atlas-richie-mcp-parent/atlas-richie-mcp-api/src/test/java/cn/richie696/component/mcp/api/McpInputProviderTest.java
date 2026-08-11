package cn.richie696.component.mcp.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpInputProvider} 作为函数式接口可被 lambda 直接实现并异步返回输入。
 */
@DisplayName("McpInputProvider 多轮输入采集回调")
class McpInputProviderTest {

    @Test
    @DisplayName("lambda 实现：按 inputRequests 返回采集到的输入")
    void shouldReturnCollectedInputs() throws Exception {
        McpInputProvider provider = requests -> CompletableFuture.completedFuture(Map.of(
                "name", "alice",
                "age", 30));

        CompletionStage<Map<String, Object>> stage = provider.collect(Map.of(
                "name", "name field hint",
                "age", "age field hint"));

        Map<String, Object> result = stage.toCompletableFuture().get();
        assertThat(result)
                .containsEntry("name", "alice")
                .containsEntry("age", 30);
    }

    @Test
    @DisplayName("允许返回不完整结果：Server 端会再调一轮")
    void shouldAllowPartialResult() throws Exception {
        McpInputProvider provider = requests -> CompletableFuture.completedFuture(Map.of("name", "alice"));

        Map<String, Object> result = provider.collect(Map.of("name", "h", "age", "h"))
                .toCompletableFuture()
                .get();

        assertThat(result).containsOnlyKeys("name");
    }

    @Test
    @DisplayName("空 inputRequests 也可调用：返回空 Map 表示无需补充")
    void shouldHandleEmptyRequests() throws Exception {
        McpInputProvider provider = requests -> CompletableFuture.completedFuture(Map.of());

        Map<String, Object> result = provider.collect(Map.of())
                .toCompletableFuture()
                .get();

        assertThat(result).isEmpty();
    }
}
