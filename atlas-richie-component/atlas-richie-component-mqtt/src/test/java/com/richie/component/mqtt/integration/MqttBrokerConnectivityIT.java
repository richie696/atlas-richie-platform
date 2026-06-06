package com.richie.component.mqtt.integration;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.richie.component.mqtt.support.MqttIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

@EnabledIf("com.richie.component.mqtt.support.MqttIntegrationTestSupport#isEnabled")
class MqttBrokerConnectivityIT {

    @Test
    void hiveMqClient_shouldConnectToTestcontainerBroker() {
        MqttIntegrationTestSupport support = MqttIntegrationTestSupport.getInstance();
        String clientId = "it-" + UUID.randomUUID();

        Mqtt5BlockingClient client = MqttClient.builder()
                .identifier(clientId)
                .serverHost(support.brokerHost())
                .serverPort(support.brokerPort())
                .useMqttVersion5()
                .buildBlocking();

        assertThatCode(() -> {
            client.connect();
            client.disconnect();
        }).doesNotThrowAnyException();
    }
}
