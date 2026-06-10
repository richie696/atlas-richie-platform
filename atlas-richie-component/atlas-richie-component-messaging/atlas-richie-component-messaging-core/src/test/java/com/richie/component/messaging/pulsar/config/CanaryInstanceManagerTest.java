package com.richie.component.messaging.pulsar.config;

import com.richie.component.messaging.config.CanaryInstanceManager;
import com.richie.contract.gateway.config.DeployConfig;
import com.richie.component.messaging.filter.CanaryMessageFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CanaryInstanceManagerTest {

    @Mock
    private DiscoveryClient discoveryClient;

    @Test
    void getCanaryInfo_includesGatewayFlags() {
        DeployConfig deployConfig = new DeployConfig();
        deployConfig.setEnable(true);
        deployConfig.setIdList(Set.of("shop-1"));
        CanaryInstanceManager manager = new CanaryInstanceManager(discoveryClient, "order-service", deployConfig);

        assertThat(manager.getCanaryInfo())
                .containsEntry("gatewayCanaryEnabled", true)
                .containsEntry("applicationName", "order-service");
    }

    @Test
    void onEnvironmentChange_clearsCanaryFilterCache() {
        DeployConfig deployConfig = new DeployConfig();
        CanaryInstanceManager manager = new CanaryInstanceManager(discoveryClient, "order-service", deployConfig);
        CanaryMessageFilter filter = mock(CanaryMessageFilter.class);
        ReflectionTestUtils.setField(manager, "canaryMessageFilter", filter);

        manager.onEnvironmentChange(new EnvironmentChangeEvent(Set.of("platform.gateway.deploy.enable")));

        verify(filter).clearCache();
    }
}
