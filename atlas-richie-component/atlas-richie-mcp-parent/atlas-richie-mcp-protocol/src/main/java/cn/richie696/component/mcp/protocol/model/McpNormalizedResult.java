package cn.richie696.component.mcp.protocol.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 归一化结果显式携带完成状态，兼容旧版本缺省语义。
 *
 * <p>设计意图：2025-11-25 时代的结果只有"完成"一种隐式状态，而 2026-07-28 起
 * 协议显式区分 {@code complete} 与 {@code input_required}。把它们抽象成统一的
 * {@link ResultType} 枚举可以让业务层在两个时代之上用同一套代码处理"是否需要再追问用户"。
 * </p>
 *
 * 关键设计：
 * <ul>
 *   <li>{@code resultType} 强制非空，避免旧协议默认值被无声丢失。</li>
 *   <li>{@code payload} 用 {@link LinkedHashMap} 拷贝后包装为不可变视图，保证顺序稳定、
 *       防止外部修改。</li>
 * </ul>
 *
 * @param resultType 结果类型（COMPLETE / INPUT_REQUIRED），必填
 * @param payload    业务负载 Map
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpNormalizedResult(ResultType resultType, Map<String, Object> payload) {
    /**
     * 紧凑构造器：归一化入参。
     */
    public McpNormalizedResult {
        resultType = Objects.requireNonNull(resultType, "resultType");
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /**
     * 结果类型枚举。
     */
    public enum ResultType {
        /** 结果已完整，可直接呈现给用户。 */
        COMPLETE,
        /** 缺少必要输入，需要继续追问用户。 */
        INPUT_REQUIRED
    }
}
