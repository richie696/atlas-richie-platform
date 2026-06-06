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
