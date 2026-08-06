package cn.richie696.component.mcp.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpProtocolNegotiatorTest {
    private final McpProtocolNegotiator negotiator = new McpProtocolNegotiator();

    @Test
    void prefersModernVersionWhenBothSidesSupportIt() {
        assertThat(negotiator.negotiate(List.of(
                McpProtocolVersions.V_2025_11_25,
                McpProtocolVersions.V_2026_07_28)))
                .isEqualTo(McpProtocolVersions.V_2026_07_28);
    }

    @Test
    void fallsBackToLegacyVersion() {
        assertThat(negotiator.negotiate(List.of(McpProtocolVersions.V_2025_11_25)))
                .isEqualTo(McpProtocolVersions.V_2025_11_25);
    }

    @Test
    void reportsSupportedVersionsWhenNegotiationFails() {
        assertThatThrownBy(() -> negotiator.negotiate(List.of("2024-11-05")))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32022);
                    assertThat(exception.data().get("supported"))
                            .isEqualTo(McpProtocolVersions.SUPPORTED);
                });
    }
}
