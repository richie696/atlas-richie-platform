package cn.richie696.gateway.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorRegistryTest {
    @Test
    void mapsHttpStatusToStableGatewayCodes() {
        assertThat(GatewayErrorRegistry.byHttpStatus(401)).isEqualTo(GatewayErrorCode.GW_AUTH_0001);
        assertThat(GatewayErrorRegistry.byHttpStatus(504)).isEqualTo(GatewayErrorCode.GW_UPSTREAM_0002);
        assertThat(GatewayErrorRegistry.byHttpStatus(418)).isEqualTo(GatewayErrorCode.GW_SYSTEM_0001);
    }

    @Test
    void buildsPublicDetailPath() {
        assertThat(GatewayErrorRegistry.helpUrl(GatewayErrorCode.GW_RATE_0001))
                .isEqualTo("/gateway/errors/GW-RATE-0001");
    }
}
