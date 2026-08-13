package cn.richie696.component.mcp.api;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Client-side MRTR input collection hook.
 *
 * <p>当 {@link McpToolResponse#resultType} 为 {@code input_required} 时，Client 需要向用户
 * 补全缺失输入后再次发起调用。{@code McpInputProvider} 即为这套"多轮 Tool 调用（Multi-Round
 * Tool Resolution, MRTR）"机制的入口回调，由上层应用实现以接入自身的输入采集通道
 * （CLI prompt、Web 表单、IM 机器人对话框等）。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpInputProvider {
    /**
     * 收集 Server 要求的输入。
     *
     * <p>实现应解析 {@code inputRequests} 中由 Server 声明的待补全字段（如缺失参数、UI Schema
     * 等），通过业务自有渠道向用户索取后，把收集到的键值对回填。允许返回不完整结果——Server
     * 会在下一轮再次调用 {@code collect}。</p>
     *
     * @param inputRequests Server 声明的待补全输入描述，键为参数名
     * @return 收集到的输入键值对的异步结果
     */
    CompletionStage<Map<String, Object>> collect(Map<String, Object> inputRequests);
}
