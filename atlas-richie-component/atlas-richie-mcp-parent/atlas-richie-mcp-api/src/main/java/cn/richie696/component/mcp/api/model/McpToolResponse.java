package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;

/**
 * Tool 的稳定调用结果。
 *
 * <p>承载一次 Tool 调用的最终输出。该 record 有意把"结果内容"与"交互状态"分列：</p>
 * <ul>
 *   <li>{@link #content} + {@link #structuredContent}：实际返回值；</li>
 *   <li>{@link #resultType}：仅取 {@code complete} 与 {@code input_required} 两值，标识调用是
 *       已经完成还是需要继续采集输入；</li>
 *   <li>{@link #inputRequests} + {@link #requestState}：当 {@code resultType=input_required} 时，
 *       携带需要采集的输入字段与多轮状态（用于在 {@code McpInputProvider} 回调中关联上下文）。</li>
 * </ul>
 *
 * <p>关键设计：把 {@code input_required} 视为正常结果而非异常。这样做的好处是：业务实现
 * 可以自然地"返回"一个需要补全的结果，Client 侧基于同一套 {@code CompletionStage<McpToolResponse>}
 * 处理正常完成与待补全两种结局，避免"用异常表达正常控制流"的反模式。</p>
 *
 * @param content 文本/多媒体内容
 * @param structuredContent 结构化内容（强类型）
 * @param error 是否失败
 * @param resultType 结果类型（{@code complete} 或 {@code input_required}）
 * @param inputRequests 待补全输入字段
 * @param requestState 多轮状态令牌
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolResponse(
        List<Map<String, Object>> content,
        Object structuredContent,
        boolean error,
        String resultType,
        Map<String, Object> inputRequests,
        String requestState) {

    /**
     * 便捷构造器：省略多轮字段，按 {@code complete} 结果构造。
     *
     * @param content 内容
     * @param structuredContent 结构化内容
     * @param error 是否失败
     */
    public McpToolResponse(
            List<Map<String, Object>> content,
            Object structuredContent,
            boolean error) {
        this(content, structuredContent, error, "complete", Map.of(), null);
    }

    /**
     * 构造一个"待补全输入"的结果。
     *
     * <p>必须至少提供 {@code inputRequests} 或 {@code requestState} 之一，否则会抛
     * {@link IllegalArgumentException}——避免 Server 端"无理由地把球抛回 Client"。</p>
     *
     * @param inputRequests 待补全字段（可为空 Map）
     * @param requestState 多轮状态令牌（不可为空字符串）
     * @return 一个 {@code resultType=input_required} 的响应
     * @throws IllegalArgumentException 当两者都为空时
     */
    public static McpToolResponse inputRequired(
            Map<String, Object> inputRequests,
            String requestState) {
        if ((inputRequests == null || inputRequests.isEmpty())
                && (requestState == null || requestState.isBlank())) {
            throw new IllegalArgumentException("input_required requires inputRequests or requestState");
        }
        return new McpToolResponse(List.of(), null, false, "input_required",
                inputRequests == null ? Map.of() : inputRequests, requestState);
    }

    /**
     * 紧凑构造器：不可变拷贝 + {@code resultType} 枚举校验 + 必填校验。
     *
     * @param content 内容
     * @param structuredContent 结构化内容
     * @param error 是否失败
     * @param resultType 结果类型
     * @param inputRequests 待补全字段
     * @param requestState 多轮状态令牌
     * @throws IllegalArgumentException 当 {@code resultType} 非法或 {@code input_required} 缺关键字段时
     */
    public McpToolResponse {
        content = content == null ? List.of() : List.copyOf(content);
        resultType = resultType == null || resultType.isBlank() ? "complete" : resultType;
        if (!resultType.equals("complete") && !resultType.equals("input_required")) {
            throw new IllegalArgumentException("Unsupported MCP resultType: " + resultType);
        }
        inputRequests = inputRequests == null ? Map.of() : Map.copyOf(inputRequests);
        if (resultType.equals("input_required") && inputRequests.isEmpty()
                && (requestState == null || requestState.isBlank())) {
            throw new IllegalArgumentException("input_required requires inputRequests or requestState");
        }
    }
}
