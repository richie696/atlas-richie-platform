package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpInputProvider;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link McpMrtrCoordinator} 的四件套：构造器对 {@code maxRounds} 的上下界校验；
 * 首轮 {@code resultType=complete} 直接返回（不再二次回调 client）；
 * 完整 MRTR 循环（两轮：第一次 {@code input_required}，第二次 {@code complete}）；
 * 当 {@code resultType=input_required} 但客户端没传 provider 时即时报错；
 * 越过 {@code maxRounds} 仍要求输入时显式抛 {@link IllegalStateException}。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpMrtrCoordinator MRTR 编舞器")
class McpMrtrCoordinatorTest {

    @Test
    @DisplayName("构造器：maxRounds=1..10 通过，越界抛 IllegalArgumentException")
    void constructorBoundsEnforced() {
        McpHttpToolClient client = Mockito.mock(McpHttpToolClient.class);
        assertThat(new McpMrtrCoordinator(client)).isNotNull();
        assertThat(new McpMrtrCoordinator(client, 1)).isNotNull();
        assertThat(new McpMrtrCoordinator(client, 10)).isNotNull();

        assertThatThrownBy(() -> new McpMrtrCoordinator(client, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new McpMrtrCoordinator(client, 11))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new McpMrtrCoordinator(client, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("构造器：client 不能为 null")
    void clientMustNotBeNull() {
        assertThatThrownBy(() -> new McpMrtrCoordinator(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("complete 结果：只调一次 client 后原样返回")
    void completeResultReturnsImmediately() {
        McpHttpToolClient client = Mockito.mock(McpHttpToolClient.class);
        McpToolResponse complete = new McpToolResponse(
                List.of(Map.of("type", "text", "text", "ok")),
                Map.of("key", "value"),
                false);
        when(client.callTool(any(URI.class), anyString(), anyMap(), anyMap(), any(), any()))
                .thenReturn(complete);

        McpMrtrCoordinator coordinator = new McpMrtrCoordinator(client, 3);
        McpToolResponse result = coordinator.callTool(
                URI.create("https://server/mcp"),
                "echo",
                Map.of("input", "ping"),
                Map.of(),
                Mockito.mock(McpInputProvider.class));

        assertThat(result).isSameAs(complete);
        verify(client, times(1)).callTool(
                any(URI.class), anyString(), anyMap(), anyMap(), any(), any());
    }

    @Test
    @DisplayName("input_required 循环：第二轮 resultType=complete 时退出")
    void inputRequiredTwoRoundsCompletes() {
        McpHttpToolClient client = Mockito.mock(McpHttpToolClient.class);
        McpToolResponse firstRound = McpToolResponse.inputRequired(
                Map.of("missing", "email"), "state-1");
        McpToolResponse secondRound = new McpToolResponse(
                List.of(Map.of("type", "text", "text", "captured")),
                Map.of("result", "completed"),
                false);
        when(client.callTool(
                any(URI.class), anyString(), anyMap(), anyMap(),
                any(Map.class), any()))
                .thenReturn(firstRound, secondRound);

        McpInputProvider provider = Mockito.mock(McpInputProvider.class);
        when(provider.collect(any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("email", "a@b.com")));

        McpMrtrCoordinator coordinator = new McpMrtrCoordinator(client, 5);
        McpToolResponse result = coordinator.callTool(
                URI.create("https://server/mcp"),
                "confirm",
                Map.of("phone", "123"),
                Map.of(),
                provider);

        assertThat(result).isSameAs(secondRound);
        verify(client, times(2)).callTool(
                any(URI.class), anyString(), anyMap(), anyMap(), any(Map.class), any());
        verify(provider, times(1)).collect(any());
    }

    @Test
    @DisplayName("input_required 但 provider 为 null：立即报错")
    void inputRequiredWithoutProviderThrows() {
        McpHttpToolClient client = Mockito.mock(McpHttpToolClient.class);
        McpToolResponse firstRound = McpToolResponse.inputRequired(
                Map.of("missing", "x"), "state-x");
        when(client.callTool(
                any(URI.class), anyString(), anyMap(), anyMap(), any(Map.class), any()))
                .thenReturn(firstRound);

        McpMrtrCoordinator coordinator = new McpMrtrCoordinator(client, 3);

        assertThatThrownBy(() -> coordinator.callTool(
                URI.create("https://server/mcp"),
                "ask",
                Map.of(),
                Map.of(),
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("input provider");
    }

    @Test
    @DisplayName("持续 input_required 直到超出 maxRounds：显式报错")
    void inputRequiredExceedsMaxRoundsThrows() {
        McpHttpToolClient client = Mockito.mock(McpHttpToolClient.class);
        McpToolResponse pending = McpToolResponse.inputRequired(
                Map.of("missing", "x"), "loop-state");
        when(client.callTool(
                any(URI.class), anyString(), anyMap(), anyMap(), any(Map.class), any()))
                .thenReturn(pending);

        McpInputProvider provider = Mockito.mock(McpInputProvider.class);
        when(provider.collect(any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("x", 1)));

        McpMrtrCoordinator coordinator = new McpMrtrCoordinator(client, 2);

        assertThatThrownBy(() -> coordinator.callTool(
                URI.create("https://server/mcp"),
                "loop",
                Map.of(),
                Map.of(),
                provider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maximum rounds");

        verify(client, times(2)).callTool(
                any(URI.class), anyString(), anyMap(), anyMap(), any(Map.class), any());
    }

    @Test
    @DisplayName("provider 既返回 null 也安全：output 仍正常")
    void providerNeverCalledForCompleteResult() {
        McpHttpToolClient client = Mockito.mock(McpHttpToolClient.class);
        when(client.callTool(
                any(URI.class), anyString(), anyMap(), anyMap(), any(Map.class), any()))
                .thenReturn(new McpToolResponse(List.of(), Map.of(), false));

        McpMrtrCoordinator coordinator = new McpMrtrCoordinator(client);
        coordinator.callTool(
                URI.create("https://server/mcp"),
                "noop",
                Map.of(),
                Map.of(),
                null);

        verify(client, times(1)).callTool(
                any(URI.class), anyString(), anyMap(), anyMap(), any(Map.class), any());
        verify(client, never()).callTool(any(), anyString(), any(), any(), any(), Mockito.anyString());
    }
}
