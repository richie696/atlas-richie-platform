package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpInputProvider;
import cn.richie696.component.mcp.api.model.McpToolResponse;

import java.net.URI;
import java.util.Map;

/**
 * 客户端侧的有限 MRTR（Multi-Round Tool Request）编舞器，将 {@code tools/call} 的
 * {@code input_required} 多轮交互模式安全地包裹成单次调用。
 *
 * <p>现代 MCP 协议允许工具在执行中向客户端索要额外输入——通过将 {@link McpToolResponse#resultType()}
 * 设为 {@code input_required}、{@link McpToolResponse#inputRequests()} 携带待补充项、
 * {@link McpToolResponse#requestState()} 作为会话标识。客户端需要：
 * (a) 收集这些输入；(b) 用相同 {@code requestState} 重发工具调用。本类自动完成这一循环，
 * 直到工具返回 {@code complete} 或达到配置的最大轮次。</p>
 *
 * <p>为何限制最大轮次（1..10）：多轮交互具有自然终止语义（问完了）；若不限制，
 * 错误实现的工具有可能导致客户端无限循环。本类强制把无界循环收敛为有限状态机，
 * 越界时显式抛错以便上层诊断。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpMrtrCoordinator {
    private final McpHttpToolClient client;
    private final int maxRounds;

    /**
     * 默认构造，默认最大轮次为 3。
     *
     * @param client 复用的 MCP HTTP 客户端，不允许为 null
     */
    public McpMrtrCoordinator(McpHttpToolClient client) {
        this(client, 3);
    }

    /**
     * 指定最大轮次的构造。
     *
     * @param client    复用的 MCP HTTP 客户端
     * @param maxRounds 多轮交互最大轮次，必须在 [1, 10] 区间
     * @throws IllegalArgumentException {@code maxRounds} 越界时
     */
    public McpMrtrCoordinator(McpHttpToolClient client, int maxRounds) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        if (maxRounds < 1 || maxRounds > 10) throw new IllegalArgumentException("maxRounds must be 1..10");
        this.maxRounds = maxRounds;
    }

    /**
     * 调用远端工具，自动编排 {@code input_required} 多轮交互。
     *
     * <p>协议状态机：
     * <ol>
     *   <li>第一轮始终以空 {@code inputResponses}、{@code null requestState} 发出。</li>
     *   <li>收到 {@code complete} 时立刻返回，调用方拿到完整结果。</li>
     *   <li>收到 {@code input_required} 时通过 {@link McpInputProvider} 收集输入，再把上一轮的
     *       {@code requestState} 与新输入作为下一轮参数；最多重试 {@link #maxRounds} 轮。</li>
     * </ol>
     *
     * @param endpoint      远端 MCP 端点 URI
     * @param toolName      工具名
     * @param arguments     首轮调用参数
     * @param headers       自定义请求头（如鉴权），每次发送都会附加
     * @param inputProvider 提供追问输入的回调；首次出现 {@code input_required} 且为 null 时直接报错
     * @return 远端工具的最终响应（{@code resultType == complete}）
     * @throws IllegalStateException 输入提供器为 null，但工具实际要求输入；或 MRTR 超过最大轮次
     */
    public McpToolResponse callTool(
            URI endpoint,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers,
            McpInputProvider inputProvider) {
        Map<String, Object> inputResponses = Map.of();
        String requestState = null;
        for (int round = 0; round < maxRounds; round++) {
            McpToolResponse response = client.callTool(
                    endpoint, toolName, arguments, headers, inputResponses, requestState);
            if (!"input_required".equals(response.resultType())) return response;
            if (inputProvider == null) {
                throw new IllegalStateException("MCP server requested input but no input provider is configured");
            }
            inputResponses = inputProvider.collect(response.inputRequests())
                    .toCompletableFuture().join();
            requestState = response.requestState();
        }
        throw new IllegalStateException("MCP MRTR exceeded configured maximum rounds");
    }
}
