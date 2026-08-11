package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolEra;

/**
 * 探测状态机的确定性输出。
 *
 * <p>三个字段共同表达"下一步该做什么"：
 * <ul>
 *   <li>{@code era} —— 当前被识别出的协议时代；{@code null} 表示尚未识别（如重试探测）。</li>
 *   <li>{@code action} —— 后续动作（使用现代 / 重试现代 / 传统握手 / 重试探测 / 不兼容失败）。</li>
 *   <li>{@code selectedVersion} —— 选定的协议版本，可能为 {@code null}（如需要重试）。</li>
 * </ul>
 * </p>
 *
 * @param era             识别出的协议时代，可能为 {@code null}
 * @param action          后续动作
 * @param selectedVersion 已选定的协议版本，可能为 {@code null}
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpProbeDecision(McpProtocolEra era, Action action, String selectedVersion) {
    /**
     * 探测状态机输出的后续动作。
     */
    public enum Action {
        /** 使用现代无状态协议直接发起请求。 */
        USE_MODERN,
        /** 再次用现代无状态协议探测。 */
        RETRY_MODERN,
        /** 切换到传统会话协议，发起 {@code initialize} 握手。 */
        INITIALIZE_LEGACY,
        /** 整体重试探测（如超时或网络抖动）。 */
        RETRY_PROBE,
        /** 不兼容，宣告失败。 */
        FAIL_INCOMPATIBLE
    }
}
