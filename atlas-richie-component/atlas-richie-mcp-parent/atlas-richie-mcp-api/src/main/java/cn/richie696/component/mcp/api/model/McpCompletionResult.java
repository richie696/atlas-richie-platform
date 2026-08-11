package cn.richie696.component.mcp.api.model;

import java.util.List;

/**
 * 参数自动补全的结果集。
 *
 * <p>承载 MCP 协议层的"参数补全"语义：当前已输入字符串的候选值集合、总数（用于分页）
 * 以及是否还有更多候选。设计上把"分页"与"列举"分离：{@link #values()} 携带当前页的候选
 * 值，{@link #total()} 与 {@link #hasMore()} 用于客户端决定是否发起下一轮补全请求。</p>
 *
 * <p>关键约束：单次返回的候选值数量上限为 100，这是 MCP 协议要求；超出将在构造期直接抛
 * {@link IllegalArgumentException}，避免把越界值传递给协议层。</p>
 *
 * @param values 当前页候选值列表
 * @param total 候选总数，可为 {@code null} 表示未知
 * @param hasMore 是否还有更多候选
 * @author richie696
 * @since 2026-08-11
 */
public record McpCompletionResult(List<String> values, Integer total, boolean hasMore) {
    /**
     * 紧凑构造器：防御性不可变拷贝 + 数量上限校验。
     *
     * @param values 当前页候选值
     * @param total 候选总数
     * @param hasMore 是否还有更多
     * @throws IllegalArgumentException 当 {@code values} 超过 100 个，或 {@code total} 为负数时
     */
    public McpCompletionResult {
        values = values == null ? List.of() : List.copyOf(values);
        if (values.size() > 100) throw new IllegalArgumentException("MCP completion values must not exceed 100");
        if (total != null && total < 0) throw new IllegalArgumentException("total must be non-negative");
    }
}
