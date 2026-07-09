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
package com.richie.component.mqtt.support;

import com.richie.testing.mqtt.MqttBrokerContainerSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

public final class MqttIntegrationTestSupport {

    private static final DockerImageName BROKER_IMAGE = DockerImageName.parse("hivemq/hivemq-ce:2024.6");
    private static final String UNAVAILABLE_MESSAGE =
            "MQTT 集成测试需要 Docker（HiveMQ CE）。CI 请设置 IT_REQUIRE_DOCKER=true。";

    private static final MqttBrokerContainerSupport DELEGATE = MqttBrokerContainerSupport.resolve(
            BROKER_IMAGE,
            UNAVAILABLE_MESSAGE,
            "MQTT");

    private MqttIntegrationTestSupport() {
    }

    public static MqttIntegrationTestSupport getInstance() {
        return Holder.INSTANCE;
    }

    public static boolean isEnabled() {
        return DELEGATE.isAvailable();
    }

    public String brokerHost() {
        return readProperty("platform.component.mqtt.server.host");
    }

    public int brokerPort() {
        return Integer.parseInt(readProperty("platform.component.mqtt.server.port"));
    }

    public void registerBrokerProperties(DynamicPropertyRegistry registry) {
        List<String> pairs = new ArrayList<>();
        appendPropertyPairs(pairs);
        pairs.forEach(pair -> {
            int eq = pair.indexOf('=');
            registry.add(pair.substring(0, eq), () -> pair.substring(eq + 1));
        });
    }

    void appendPropertyPairs(List<String> pairs) {
        DELEGATE.appendConnectionPropertyPairs(pairs);
    }

    private String readProperty(String key) {
        List<String> pairs = new ArrayList<>();
        appendPropertyPairs(pairs);
        return pairs.stream()
                .filter(p -> p.startsWith(key + "="))
                .map(p -> p.substring(key.length() + 1))
                .findFirst()
                .orElseThrow();
    }

    private static final class Holder {
        private static final MqttIntegrationTestSupport INSTANCE = new MqttIntegrationTestSupport();
    }
}
