/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.messaging.filter;

import com.richie.contract.constant.GlobalConstants;
import com.richie.contract.gateway.config.DeployConfig;
import com.richie.component.messaging.config.CanaryInstanceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanaryMessageFilterTest {

    @Mock
    private DiscoveryClient discoveryClient;

    @Test
    void shouldProcess_allowsAllWhenCanaryDisabled() {
        DeployConfig deployConfig = new DeployConfig();
        deployConfig.setEnable(false);
        CanaryMessageFilter filter = new CanaryMessageFilter(deployConfig, discoveryClient, "order-service", null);

        assertThat(filter.shouldProcess(MessageBuilder.withPayload("x").build())).isTrue();
    }

    @Test
    void shouldProcess_routesCanaryMessageToCanaryInstance() {
        DeployConfig deployConfig = new DeployConfig();
        deployConfig.setEnable(true);
        deployConfig.setServiceList(Set.of("order-service"));
        deployConfig.setIdList(Set.of("shop-1"));

        CanaryInstanceManager manager = mock(CanaryInstanceManager.class);
        when(manager.isCanaryInstance()).thenReturn(true);
        CanaryMessageFilter filter = new CanaryMessageFilter(deployConfig, discoveryClient, "order-service", manager);

        Message<String> canaryMessage = MessageBuilder.withPayload("x")
                .setHeader(GlobalConstants.X_CANARY_ID, "shop-1")
                .build();
        Message<String> normalMessage = MessageBuilder.withPayload("x").build();

        assertThat(filter.shouldProcess(canaryMessage)).isTrue();
        assertThat(filter.shouldProcess(normalMessage)).isFalse();
    }

    @Test
    void shouldProcess_skipsWhenServiceNotInCanaryList() {
        DeployConfig deployConfig = new DeployConfig();
        deployConfig.setEnable(true);
        deployConfig.setServiceList(Set.of("other-service"));
        CanaryMessageFilter filter = new CanaryMessageFilter(deployConfig, discoveryClient, "order-service", null);

        assertThat(filter.shouldProcess(MessageBuilder.withPayload("x").build())).isTrue();
    }

    @Test
    void shouldProcess_normalInstanceRejectsCanaryMessage() {
        DeployConfig deployConfig = new DeployConfig();
        deployConfig.setEnable(true);
        deployConfig.setServiceList(Set.of("order-service"));
        deployConfig.setIdList(Set.of("shop-1"));

        CanaryInstanceManager manager = mock(CanaryInstanceManager.class);
        when(manager.isCanaryInstance()).thenReturn(false);
        CanaryMessageFilter filter = new CanaryMessageFilter(deployConfig, discoveryClient, "order-service", manager);

        Message<String> canaryMessage = MessageBuilder.withPayload("x")
                .setHeader(GlobalConstants.X_CANARY_ID, "shop-1")
                .build();
        assertThat(filter.shouldProcess(canaryMessage)).isFalse();
    }

    @Test
    void clearCache_resetsDiscoveryFallback() {
        DeployConfig deployConfig = new DeployConfig();
        deployConfig.setEnable(true);
        deployConfig.setServiceList(Set.of("order-service"));

        ServiceInstance instance = mock(ServiceInstance.class);
        when(instance.getMetadata()).thenReturn(Map.of(
                GlobalConstants.SERVER_CANARY_ENV, "false",
                GlobalConstants.SERVER_CANARY_CATEGORY, "ID"));
        when(discoveryClient.getInstances("order-service")).thenReturn(List.of(instance));

        CanaryMessageFilter filter = new CanaryMessageFilter(deployConfig, discoveryClient, "order-service", null);
        Message<String> normalMessage = MessageBuilder.withPayload("x").build();

        assertThat(filter.shouldProcess(normalMessage)).isTrue();
        filter.clearCache();
        assertThat(filter.shouldProcess(normalMessage)).isTrue();
    }

    @Test
    void shouldProcess_whenDiscoveryFails_defaultsNonCanaryInstance() {
        DeployConfig deployConfig = new DeployConfig();
        deployConfig.setEnable(true);
        deployConfig.setServiceList(Set.of("order-service"));
        when(discoveryClient.getInstances(anyString())).thenThrow(new RuntimeException("discovery down"));

        CanaryMessageFilter filter = new CanaryMessageFilter(deployConfig, discoveryClient, "order-service", null);
        assertThat(filter.shouldProcess(MessageBuilder.withPayload("x").build())).isTrue();
    }
}
