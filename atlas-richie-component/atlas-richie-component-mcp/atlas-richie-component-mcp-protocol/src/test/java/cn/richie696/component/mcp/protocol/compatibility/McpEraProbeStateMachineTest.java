package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolEra;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpEraProbeStateMachineTest {
    private final McpEraProbeStateMachine machine = new McpEraProbeStateMachine();

    @Test
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
    void stdioOtherErrorAndTimeoutFallBackToInitialize() {
        assertThat(evaluate(
                McpTransportBinding.STDIO,
                McpProbeEvent.jsonRpcError(null, -32601, List.of())).action())
                .isEqualTo(McpProbeDecision.Action.INITIALIZE_LEGACY);
        assertThat(evaluate(McpTransportBinding.STDIO, McpProbeEvent.timeout()).action())
                .isEqualTo(McpProbeDecision.Action.INITIALIZE_LEGACY);
    }

    @Test
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
    void http404MethodNotFoundBodyIdentifiesModernEndpoint() {
        McpProbeDecision decision = evaluate(
                McpTransportBinding.STREAMABLE_HTTP,
                McpProbeEvent.jsonRpcError(404, -32601, List.of()));

        assertThat(decision.era()).isEqualTo(McpProtocolEra.STATELESS_2026);
        assertThat(decision.action()).isEqualTo(McpProbeDecision.Action.USE_MODERN);
    }

    @Test
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
    void modernServerWithNoCommonModernVersionIsIncompatible() {
        McpProbeDecision decision = evaluate(
                McpTransportBinding.STDIO,
                McpProbeEvent.discoverResult(List.of(McpProtocolVersions.V_2025_11_25)));

        assertThat(decision.era()).isEqualTo(McpProtocolEra.STATELESS_2026);
        assertThat(decision.action()).isEqualTo(McpProbeDecision.Action.FAIL_INCOMPATIBLE);
    }

    private McpProbeDecision evaluate(McpTransportBinding binding, McpProbeEvent event) {
        return machine.evaluate(binding, McpProtocolVersions.V_2026_07_28, event);
    }
}
