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
package com.richie.testing.mqtt;

import com.richie.testing.container.ContainerMode;
import com.richie.testing.docker.TestcontainersEnvironment;
import com.richie.testing.env.IntegrationTestPolicy;
import com.richie.testing.env.TestEnv;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

/**
 * HiveMQ CE MQTT Broker 集测连接解析：默认 Testcontainers，外部 Broker 需显式 opt-in。
 */
public final class MqttBrokerContainerSupport {

    private final String[] envPrefixes;
    private final DockerImageName image;
    private final String unavailableMessage;

    private final ContainerMode mode;
    private final String skipReason;
    private final String host;
    private final int port;
    @SuppressWarnings("resource")
    private final GenericContainer<?> container;

    static {
        TestcontainersEnvironment.ensureConfigured();
    }

    private MqttBrokerContainerSupport(
            String[] envPrefixes,
            DockerImageName image,
            String unavailableMessage,
            ContainerMode mode,
            String skipReason,
            String host,
            int port,
            GenericContainer<?> container) {
        this.envPrefixes = envPrefixes;
        this.image = image;
        this.unavailableMessage = unavailableMessage;
        this.mode = mode;
        this.skipReason = skipReason;
        this.host = host;
        this.port = port;
        this.container = container;
    }

    public static MqttBrokerContainerSupport resolve(
            DockerImageName image,
            String unavailableMessage,
            String... envPrefixes) {
        boolean requireDocker = IntegrationTestPolicy.requireDocker(envPrefixes);
        boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();

        if (requireDocker && !dockerAvailable) {
            throw new IllegalStateException(
                    "IT_REQUIRE_DOCKER=true but Docker is not available. "
                            + "Install Docker Desktop / enable CI dind.");
        }

        if (dockerAvailable) {
            return startTestcontainers(image, unavailableMessage, envPrefixes);
        }

        if (IntegrationTestPolicy.useExternal(envPrefixes)) {
            MqttBrokerContainerSupport external = resolveExternal(envPrefixes, unavailableMessage);
            if (external != null) {
                return external;
            }
            throw new IllegalStateException(
                    "IT_USE_EXTERNAL=true but no MQTT broker configured. "
                            + "Set MQTT_IT_HOST/PORT.");
        }

        return new MqttBrokerContainerSupport(
                envPrefixes,
                image,
                unavailableMessage,
                ContainerMode.UNAVAILABLE,
                unavailableMessage,
                null,
                0,
                null);
    }

    public boolean isAvailable() {
        return mode != ContainerMode.UNAVAILABLE;
    }

    public boolean isExternal() {
        return mode == ContainerMode.EXTERNAL;
    }

    public String skipReason() {
        return skipReason;
    }

    public List<String> appendConnectionPropertyPairs(List<String> pairs) {
        if (host != null) {
            pairs.add("platform.component.mqtt.server.host=" + host);
            pairs.add("platform.component.mqtt.server.port=" + port);
        }
        pairs.add("platform.component.mqtt.enable=true");
        pairs.add("platform.component.mqtt.init-client=false");
        return pairs;
    }

    @SuppressWarnings("resource")
    private static MqttBrokerContainerSupport startTestcontainers(
            DockerImageName image,
            String unavailableMessage,
            String... envPrefixes) {
        GenericContainer<?> broker = new GenericContainer<>(image)
                .withExposedPorts(1883)
                .waitingFor(Wait.forListeningPort());
        broker.start();
        Runtime.getRuntime().addShutdownHook(new Thread(broker::stop));

        return new MqttBrokerContainerSupport(
                envPrefixes,
                image,
                unavailableMessage,
                ContainerMode.TESTCONTAINERS,
                null,
                broker.getHost(),
                broker.getMappedPort(1883),
                broker);
    }

    private static MqttBrokerContainerSupport resolveExternal(
            String[] envPrefixes,
            String unavailableMessage) {
        String host = firstEnv(envPrefixes, "HOST", "host");
        if (host != null) {
            int port = Integer.parseInt(firstEnv(envPrefixes, "PORT", "port", "1883"));
            return new MqttBrokerContainerSupport(
                    envPrefixes,
                    imagePlaceholder(),
                    unavailableMessage,
                    ContainerMode.EXTERNAL,
                    null,
                    host,
                    port,
                    null);
        }
        return null;
    }

    private static String firstEnv(String[] envPrefixes, String suffix, String propertySuffix, String... defaults) {
        List<String> envKeys = new ArrayList<>();
        List<String> propertyKeys = new ArrayList<>();
        for (String prefix : envPrefixes) {
            envKeys.add(prefix + "_IT_" + suffix);
            propertyKeys.add(toPropertyKey(prefix) + ".it." + propertySuffix);
        }
        envKeys.add("MQTT_IT_" + suffix);
        propertyKeys.add("mqtt.it." + propertySuffix);
        return TestEnv.firstResolved(
                envKeys.toArray(String[]::new),
                propertyKeys.toArray(String[]::new),
                defaults);
    }

    private static String firstEnv(String[] envPrefixes, String suffix, String propertySuffix) {
        return firstEnv(envPrefixes, suffix, propertySuffix, new String[0]);
    }

    private static String toPropertyKey(String prefix) {
        return prefix.toLowerCase().replace('_', '.');
    }

    private static DockerImageName imagePlaceholder() {
        return DockerImageName.parse("hivemq/hivemq-ce:2024.6");
    }
}
