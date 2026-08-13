package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolEra;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolNegotiator;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 按 transport binding 执行 modern/legacy era 判定的状态机。
 *
 * <p>设计意图：客户端在与远端 MCP server 完成 {@code initialize} 握手之前不知道对方支持
 * 哪个协议时代。本状态机接收一个已归一化的 {@link McpProbeEvent}，结合
 * {@link McpTransportBinding}，输出确定性的 {@link McpProbeDecision}。</p>
 *
 * 关键判定规则（按 {@code event.type()} 分发）：
 * <ul>
 *   <li>{@code DISCOVER_RESULT} —— 根据对端声明的版本列表做协商；只有 2026-07-28 才被接受。</li>
 *   <li>{@code MODERN_SUCCESS} —— 探测成功，直接走无状态路径。</li>
 *   <li>{@code JSON_RPC_ERROR} —— 区分"现代错误码"（{@code -32020/-32021/-32022}）
 *       与"传统错误码"；STDIO 传输下所有错误降级为传统初始化。</li>
 *   <li>{@code TRANSPORT_ERROR / TIMEOUT} —— STDIO 直接降级；HTTP 4xx 降级、5xx 重试。</li>
 * </ul>
 *
 * <p>为什么"现代错误码"集合来自协议规范：MCP 2026-07-28 引入了
 * {@code -32020}/{@code -32021}/{@code -32022} 三个错误码（header mismatch、missing
 * capability、unsupported version），收到这些错误基本可以确认对端是现代方言但当前请求
 * 不被接受——可以放心用现代路径重试或切换。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpEraProbeStateMachine {
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int HEADER_MISMATCH = -32020;
    private static final int MISSING_REQUIRED_CLIENT_CAPABILITY = -32021;
    private static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;
    private static final Set<Integer> MCP_MODERN_ERRORS = Set.of(
            HEADER_MISMATCH,
            MISSING_REQUIRED_CLIENT_CAPABILITY,
            UNSUPPORTED_PROTOCOL_VERSION);

    private final McpProtocolNegotiator negotiator;

    /**
     * 使用默认协商器构造状态机。
     */
    public McpEraProbeStateMachine() {
        this(new McpProtocolNegotiator());
    }

    /**
     * 使用自定义协商器构造状态机（便于测试或定制偏好顺序）。
     *
     * @param negotiator 协商器实例
     */
    public McpEraProbeStateMachine(McpProtocolNegotiator negotiator) {
        this.negotiator = Objects.requireNonNull(negotiator, "negotiator");
    }

    /**
     * 根据探测事件、传输绑定与请求版本，输出下一步动作。
     *
     * @param binding          当前传输绑定
     * @param requestedVersion 本次探测请求的协议版本
     * @param event            探测事件
     * @return 状态机输出（时代 + 动作 + 选定版本）
     * @throws McpProtocolException 当 {@code DISCOVER_RESULT} 缺失声明版本时
     */
    public McpProbeDecision evaluate(
            McpTransportBinding binding,
            String requestedVersion,
            McpProbeEvent event) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(requestedVersion, "requestedVersion");
        Objects.requireNonNull(event, "event");

        return switch (event.type()) {
            case DISCOVER_RESULT -> selectAdvertised(event.advertisedVersions(), false);
            case MODERN_SUCCESS -> modern(requestedVersion, McpProbeDecision.Action.USE_MODERN);
            case JSON_RPC_ERROR -> jsonRpcError(binding, requestedVersion, event);
            case TRANSPORT_ERROR -> transportFailure(binding, event.httpStatus());
            case TIMEOUT -> timeout(binding);
        };
    }

    private McpProbeDecision jsonRpcError(
            McpTransportBinding binding,
            String requestedVersion,
            McpProbeEvent event) {
        int code = Objects.requireNonNull(event.jsonRpcErrorCode(), "jsonRpcErrorCode");
        if (code == UNSUPPORTED_PROTOCOL_VERSION) {
            return selectAdvertised(event.advertisedVersions(), true);
        }
        if (MCP_MODERN_ERRORS.contains(code)
                || binding == McpTransportBinding.STREAMABLE_HTTP
                && Integer.valueOf(404).equals(event.httpStatus())
                && code == METHOD_NOT_FOUND) {
            return modern(requestedVersion, McpProbeDecision.Action.USE_MODERN);
        }
        return binding == McpTransportBinding.STDIO
                ? legacy()
                : httpUnrecognized(event.httpStatus());
    }

    private McpProbeDecision transportFailure(McpTransportBinding binding, Integer httpStatus) {
        if (binding == McpTransportBinding.STDIO) {
            return legacy();
        }
        return httpUnrecognized(httpStatus);
    }

    private McpProbeDecision timeout(McpTransportBinding binding) {
        return binding == McpTransportBinding.STDIO
                ? legacy()
                : new McpProbeDecision(null, McpProbeDecision.Action.RETRY_PROBE, null);
    }

    private McpProbeDecision httpUnrecognized(Integer httpStatus) {
        if (httpStatus != null && httpStatus >= 400 && httpStatus < 500) {
            return legacy();
        }
        return new McpProbeDecision(null, McpProbeDecision.Action.RETRY_PROBE, null);
    }

    private McpProbeDecision selectAdvertised(List<String> advertisedVersions, boolean retry) {
        if (advertisedVersions == null || advertisedVersions.isEmpty()) {
            throw new McpProtocolException(
                    "MCP_INVALID_PROBE_RESPONSE",
                    -32602,
                    "Modern probe response did not advertise supported protocol versions",
                    Map.of());
        }
        try {
            String selected = negotiator.negotiate(advertisedVersions);
            if (McpProtocolVersions.V_2025_11_25.equals(selected)) {
                // 旧版 advertise 中只声明了 2025-11-25，本组件不接受传统时代作为最终结果
                return new McpProbeDecision(
                        McpProtocolEra.STATELESS_2026,
                        McpProbeDecision.Action.FAIL_INCOMPATIBLE,
                        null);
            }
            return modern(
                    selected,
                    retry ? McpProbeDecision.Action.RETRY_MODERN : McpProbeDecision.Action.USE_MODERN);
        } catch (McpProtocolException exception) {
            return new McpProbeDecision(
                    McpProtocolEra.STATELESS_2026,
                    McpProbeDecision.Action.FAIL_INCOMPATIBLE,
                    null);
        }
    }

    private McpProbeDecision modern(String version, McpProbeDecision.Action action) {
        return new McpProbeDecision(McpProtocolEra.STATELESS_2026, action, version);
    }

    private McpProbeDecision legacy() {
        return new McpProbeDecision(
                McpProtocolEra.SESSION_2025,
                McpProbeDecision.Action.INITIALIZE_LEGACY,
                null);
    }
}
