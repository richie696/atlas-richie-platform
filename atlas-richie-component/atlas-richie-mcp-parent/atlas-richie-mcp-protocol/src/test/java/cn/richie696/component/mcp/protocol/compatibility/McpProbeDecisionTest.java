package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolEra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpProbeDecision} 及其 {@link McpProbeDecision.Action} 枚举的稳定性：
 * 5 种动作（USE_MODERN / RETRY_MODERN / INITIALIZE_LEGACY / RETRY_PROBE /
 * FAIL_INCOMPATIBLE）必须完整且命名稳定，确保探测状态机决策结果可被业务侧稳定路由。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpProbeDecision 探测决策")
class McpProbeDecisionTest {

    @Test
    @DisplayName("Action 枚举覆盖探测状态机的全部决策动作")
    void actionEnumCoversAllDecisions() {
        assertThat(McpProbeDecision.Action.values())
                .containsExactlyInAnyOrder(
                        McpProbeDecision.Action.USE_MODERN,
                        McpProbeDecision.Action.RETRY_MODERN,
                        McpProbeDecision.Action.INITIALIZE_LEGACY,
                        McpProbeDecision.Action.RETRY_PROBE,
                        McpProbeDecision.Action.FAIL_INCOMPATIBLE);
    }

    @Test
    @DisplayName("record 三个字段直接访问")
    void recordExposesAllFields() {
        McpProbeDecision decision = new McpProbeDecision(
                McpProtocolEra.STATELESS_2026,
                McpProbeDecision.Action.USE_MODERN,
                "2026-07-28");

        assertThat(decision.era()).isEqualTo(McpProtocolEra.STATELESS_2026);
        assertThat(decision.action()).isEqualTo(McpProbeDecision.Action.USE_MODERN);
        assertThat(decision.selectedVersion()).isEqualTo("2026-07-28");
    }

    @Test
    @DisplayName("null 字段允许（如 RETRY_PROBE 不需要选版本）")
    void nullableFieldsAreAllowed() {
        McpProbeDecision decision = new McpProbeDecision(
                null, McpProbeDecision.Action.RETRY_PROBE, null);

        assertThat(decision.era()).isNull();
        assertThat(decision.selectedVersion()).isNull();
    }
}
