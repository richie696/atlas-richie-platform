package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolEra;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolNegotiator;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpEraProbeStateMachine} 在版本探测阶段对不同 transport binding 下
 * {@link McpProbeEvent} 的归类与决策：STDIO 上的 discover 响应、规格定义的 modern
 * 错误码、其它错误与超时；HTTP 上的 404 + method-not-found、不可识别 4xx / 5xx / 超时
 * 等典型场景。每个事件都映射到 {@link McpProbeDecision}，决定后续应
 * {@code USE_MODERN}/{@code RETRY_MODERN}/{@code INITIALIZE_LEGACY}/
 * {@code RETRY_PROBE}/{@code FAIL_INCOMPATIBLE} 哪一种动作。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpEraProbeStateMachine 探测状态机")
class McpEraProbeStateMachineTest {

    private final McpEraProbeStateMachine machine = new McpEraProbeStateMachine();

    @Test
    @DisplayName("DISCOVER_RESULT 命中 modern 时返回 USE_MODERN + modern 版本")
    void discoverResultSelectsModernVersion() {
        McpProbeDecision decision = evaluate(
                McpTransportBinding.STDIO,
                McpProbeEvent.discoverResult(List.of(
                        McpProtocolVersions.V_2025_11_25,
                        McpProtocolVersions.V_2026_07_28)));

        assertThat(decision).isEqualTo(new McpProbeDecision(
                McpProtocolEra.STATELESS_2026,
                McpProbeDecision.Action.USE_MODERN,
                McpProtocolVersions.V_2026_07_28));
    }

    @Test
    @DisplayName("UNSUPPORTED_PROTOCOL_VERSION 错误时 RETRY_MODERN（retry=true）")
    void unsupportedVersionIsModernAndRetriesWithoutLegacyFallback() {
        McpProbeDecision decision = evaluate(
                McpTransportBinding.STDIO,
                McpProbeEvent.jsonRpcError(
                        null,
                        -32022,
                        List.of(McpProtocolVersions.V_2026_07_28)));

        assertThat(decision.action()).isEqualTo(McpProbeDecision.Action.RETRY_MODERN);
        assertThat(decision.era()).isEqualTo(McpProtocolEra.STATELESS_2026);
    }

    @Test
    @DisplayName("STDIO 上的其他错误码与超时统一降级到 INITIALIZE_LEGACY")
    void stdioOtherErrorAndTimeoutFallBackToInitialize() {
        assertThat(evaluate(
                McpTransportBinding.STDIO,
                McpProbeEvent.jsonRpcError(null, -32601, List.of())).action())
                .isEqualTo(McpProbeDecision.Action.INITIALIZE_LEGACY);
        assertThat(evaluate(McpTransportBinding.STDIO, McpProbeEvent.timeout()).action())
                .isEqualTo(McpProbeDecision.Action.INITIALIZE_LEGACY);
    }

    @Test
    @DisplayName("STDIO 上收到规格定义的 modern 错误码（-32020/-32021）识别为 modern")
    void stdioRecognizesSpecificationOwnedModernErrors() {
        assertThat(evaluate(
                McpTransportBinding.STDIO,
                McpProbeEvent.jsonRpcError(null, -32020, List.of())).era())
                .isEqualTo(McpProtocolEra.STATELESS_2026);
        assertThat(evaluate(
                McpTransportBinding.STDIO,
                McpProbeEvent.jsonRpcError(null, -32021, List.of())).era())
                .isEqualTo(McpProtocolEra.STATELESS_2026);
    }

    @Test
    @DisplayName("STREAMABLE_HTTP 上 404 + METHOD_NOT_FOUND 识别为 USE_MODERN")
    void http404MethodNotFoundBodyIdentifiesModernEndpoint() {
        McpProbeDecision decision = evaluate(
                McpTransportBinding.STREAMABLE_HTTP,
                McpProbeEvent.jsonRpcError(404, -32601, List.of()));

        assertThat(decision.era()).isEqualTo(McpProtocolEra.STATELESS_2026);
        assertThat(decision.action()).isEqualTo(McpProbeDecision.Action.USE_MODERN);
    }

    @Test
    @DisplayName("STREAMABLE_HTTP：4xx 降级、5xx 与超时重试探测")
    void httpUnrecognized4xxFallsBackBut5xxAndTimeoutRetryProbe() {
        assertThat(evaluate(
                McpTransportBinding.STREAMABLE_HTTP,
                McpProbeEvent.transportError(404)).action())
                .isEqualTo(McpProbeDecision.Action.INITIALIZE_LEGACY);
        assertThat(evaluate(
                McpTransportBinding.STREAMABLE_HTTP,
                McpProbeEvent.transportError(503)).action())
                .isEqualTo(McpProbeDecision.Action.RETRY_PROBE);
        assertThat(evaluate(McpTransportBinding.STREAMABLE_HTTP, McpProbeEvent.timeout()).action())
                .isEqualTo(McpProbeDecision.Action.RETRY_PROBE);
    }

    @Test
    @DisplayName("STREAMABLE_HTTP：transportError(null) 视为不可识别 → RETRY_PROBE")
    void httpTransportErrorWithNullStatusRetriesProbe() {
        assertThat(evaluate(
                McpTransportBinding.STREAMABLE_HTTP,
                McpProbeEvent.transportError(null)).action())
                .isEqualTo(McpProbeDecision.Action.RETRY_PROBE);
    }

    @Test
    @DisplayName("STREAMABLE_HTTP：jsonRpcError 不可识别码 + null status → RETRY_PROBE")
    void httpJsonRpcErrorWithNullStatusRetriesProbe() {
        assertThat(evaluate(
                McpTransportBinding.STREAMABLE_HTTP,
                McpProbeEvent.jsonRpcError(null, -32600, List.of())).action())
                .isEqualTo(McpProbeDecision.Action.RETRY_PROBE);
    }

    @Test
    @DisplayName("DISCOVER_RESULT 只声明 legacy 版本时返回 FAIL_INCOMPATIBLE")
    void modernServerWithNoCommonModernVersionIsIncompatible() {
        McpProbeDecision decision = evaluate(
                McpTransportBinding.STDIO,
                McpProbeEvent.discoverResult(List.of(McpProtocolVersions.V_2025_11_25)));

        assertThat(decision.era()).isEqualTo(McpProtocolEra.STATELESS_2026);
        assertThat(decision.action()).isEqualTo(McpProbeDecision.Action.FAIL_INCOMPATIBLE);
    }

    @Test
    @DisplayName("DISCOVER_RESULT 声明 null / 空列表时抛 -32602（响应不合法）")
    void discoverResultWithMissingVersionsThrows() {
        assertThatThrownBy(() -> evaluate(
                McpTransportBinding.STDIO,
                McpProbeEvent.discoverResult(null)))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32602);
                    assertThat(exception.errorCode()).isEqualTo("MCP_INVALID_PROBE_RESPONSE");
                });
        assertThatThrownBy(() -> evaluate(
                McpTransportBinding.STDIO,
                McpProbeEvent.discoverResult(List.of())))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("MODERN_SUCCESS 事件直接走 USE_MODERN + requestedVersion")
    void modernSuccessUsesRequestedVersion() {
        McpProbeDecision decision = evaluate(
                McpTransportBinding.STREAMABLE_HTTP,
                McpProbeEvent.modernSuccess(200));

        assertThat(decision.action()).isEqualTo(McpProbeDecision.Action.USE_MODERN);
        assertThat(decision.era()).isEqualTo(McpProtocolEra.STATELESS_2026);
        assertThat(decision.selectedVersion()).isEqualTo(McpProtocolVersions.V_2026_07_28);
    }

    @Test
    @DisplayName("binding / requestedVersion / event 任一为 null 都抛 NPE")
    void rejectsNullArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> machine.evaluate(null, "v", McpProbeEvent.timeout()));
        assertThatNullPointerException()
                .isThrownBy(() -> machine.evaluate(McpTransportBinding.STDIO, null, McpProbeEvent.timeout()));
        assertThatNullPointerException()
                .isThrownBy(() -> machine.evaluate(McpTransportBinding.STDIO, "v", null));
    }

    @Test
    @DisplayName("JSON_RPC_ERROR 事件缺失 jsonRpcErrorCode 时抛 NPE（信息缺失无法判定）")
    void jsonRpcErrorWithoutCodeIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> evaluate(
                        McpTransportBinding.STDIO,
                        new McpProbeEvent(
                                McpProbeEvent.Type.JSON_RPC_ERROR, null, null, List.of())));
    }

    @Test
    @DisplayName("DISCOVER_RESULT + 不与本组件任何支持版本相交 → FAIL_INCOMPATIBLE")
    void discoverResultWithNoCommonVersionIsIncompatible() {
        McpEraProbeStateMachine custom = new McpEraProbeStateMachine(
                new McpProtocolNegotiator(List.of(McpProtocolVersions.V_2026_07_28)));
        McpProbeDecision decision = custom.evaluate(
                McpTransportBinding.STDIO,
                McpProtocolVersions.V_2026_07_28,
                McpProbeEvent.discoverResult(List.of("2024-01-01")));

        assertThat(decision.action()).isEqualTo(McpProbeDecision.Action.FAIL_INCOMPATIBLE);
    }

    @Test
    @DisplayName("custom 协商器构造时传 null 抛 NPE")
    void rejectsNullCustomNegotiator() {
        assertThatNullPointerException()
                .isThrownBy(() -> new McpEraProbeStateMachine(null))
                .withMessageContaining("negotiator");
    }

    private McpProbeDecision evaluate(McpTransportBinding binding, McpProbeEvent event) {
        return machine.evaluate(binding, McpProtocolVersions.V_2026_07_28, event);
    }
}
