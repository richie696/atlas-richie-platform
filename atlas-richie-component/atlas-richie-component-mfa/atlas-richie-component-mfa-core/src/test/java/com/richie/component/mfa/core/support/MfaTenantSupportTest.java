package com.richie.component.mfa.core.support;

import com.richie.contract.gateway.config.GatewayContract;
import com.richie.contract.gateway.config.TenantFilterConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MfaTenantSupportTest {

    @Test
    void isTenantEnabled_prefersGatewayContract() {
        MfaTenantSupport support = new MfaTenantSupport();
        TenantFilterConfig tenant = new TenantFilterConfig();
        tenant.setEnable(true);
        GatewayContract gatewayContract = new GatewayContract();
        gatewayContract.setTenant(tenant);
        TenantFilterConfig fallback = new TenantFilterConfig();
        fallback.setEnable(false);
        ReflectionTestUtils.setField(support, "gatewayContract", gatewayContract);
        ReflectionTestUtils.setField(support, "tenantFilterConfig", fallback);

        assertThat(support.isTenantEnabled()).isTrue();
    }

    @Test
    void isTenantEnabled_fallsBackToTenantFilterConfig() {
        MfaTenantSupport support = new MfaTenantSupport();
        TenantFilterConfig tenantFilter = new TenantFilterConfig();
        tenantFilter.setEnable(true);
        ReflectionTestUtils.setField(support, "tenantFilterConfig", tenantFilter);

        assertThat(support.isTenantEnabled()).isTrue();
    }

    @Test
    void isTenantEnabled_defaultsToFalseWhenNoConfig() {
        assertThat(new MfaTenantSupport().isTenantEnabled()).isFalse();
    }
}
