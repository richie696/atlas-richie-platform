/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.messaging.pulsar.config;

import cn.richie696.component.messaging.config.CanaryInstanceManager;
import cn.richie696.component.messaging.filter.CanaryMessageFilter;
import cn.richie696.contract.gateway.config.DeployConfig;
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
