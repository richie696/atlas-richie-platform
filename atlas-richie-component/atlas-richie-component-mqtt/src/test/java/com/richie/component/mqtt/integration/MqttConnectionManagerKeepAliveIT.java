package com.richie.component.mqtt.integration;

import com.hivemq.client.mqtt.MqttClientState;
import com.richie.component.mqtt.beans.ConnectionState;
import com.richie.component.mqtt.beans.ConnectionStateEvent;
import com.richie.component.mqtt.client.ConnectionManager;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.config.ServerInfo;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.richie.component.mqtt.support.MqttIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for {@link ConnectionManager} against a real HiveMQ CE
 * testcontainer broker. Verifies the connection lifecycle (connect, disconnect, reconnect)
 * and the abnormal-disconnect safe-reconnect path that is the most critical "keep-alive"
 * behavior the component relies on in production.
 */
@EnabledIf("com.richie.component.mqtt.support.MqttIntegrationTestSupport#isEnabled")
class MqttConnectionManagerKeepAliveIT {

    private ConnectionManager connectionManager;

    @AfterEach
    void shutdown() {
        if (connectionManager != null) {
            try {
                connectionManager.disconnect();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    @Test
    void connect_thenDisconnect_thenConnectAgain_fullLifecycle() throws Exception {
        connectionManager = newConnectionManager("e2e-lifecycle-" + UUID.randomUUID(), true);

        assertThat(connectionManager.connect()).isTrue();
        assertThat(connectionManager.getMqttClient().getState()).isEqualTo(MqttClientState.CONNECTED);

        connectionManager.disconnect();
        awaitState(connectionManager, MqttClientState.DISCONNECTED, 5_000L);

        assertThat(connectionManager.connect()).isTrue();
        assertThat(connectionManager.getMqttClient().getState()).isEqualTo(MqttClientState.CONNECTED);
    }

    @Test
    void handleAbnormalDisconnectEvent_triggersSafeReconnect() throws Exception {
        connectionManager = newConnectionManager("e2e-reconnect-" + UUID.randomUUID(), true);

        assertThat(connectionManager.connect()).isTrue();
        assertThat(connectionManager.getMqttClient().getState()).isEqualTo(MqttClientState.CONNECTED);

        connectionManager.disconnect();
        awaitState(connectionManager, MqttClientState.DISCONNECTED, 5_000L);

        connectionManager.handleConnectionStateEvent(
                ConnectionStateEvent.of(connectionManager.getMqttClient()
                                .getConfig().getClientIdentifier().toString(),
                        ConnectionState.ABNORMAL_DISCONNECT, NetworkTypeEnum.PUBLIC));

        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline
                && connectionManager.getMqttClient().getState() != MqttClientState.CONNECTED) {
            Thread.sleep(100L);
        }

        assertThat(connectionManager.getMqttClient().getState())
                .as("safeReconnect should bring the client back to CONNECTED within 10s")
                .isEqualTo(MqttClientState.CONNECTED);
    }

    @Test
    void getCurrentConnectionDuration_returnsPositiveValue_whileConnected() throws Exception {
        connectionManager = newConnectionManager("e2e-duration-" + UUID.randomUUID(), true);

        assertThat(connectionManager.connect()).isTrue();
        Thread.sleep(200L);

        long duration = connectionManager.getCurrentConnectionDuration();
        assertThat(duration)
                .as("duration must be non-zero while connected")
                .isGreaterThan(0L)
                .isLessThan(10_000L);
    }

    @Test
    void waitForConnected_returnsWithinTimeout_afterConnect() throws Exception {
        connectionManager = newConnectionManager("e2e-wait-" + UUID.randomUUID(), true);

        Thread waiter = new Thread(() -> {
            try {
                connectionManager.waitForConnected();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();

        assertThat(connectionManager.connect()).isTrue();

        waiter.join(5_000L);
        assertThat(waiter.isAlive())
                .as("waitForConnected should return once CONNECTED state is reached")
                .isFalse();
    }

    private void awaitState(ConnectionManager manager, MqttClientState expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (manager.getMqttClient().getState() == expected) {
                return;
            }
            Thread.sleep(50L);
        }
        assertThat(manager.getMqttClient().getState())
                .as("state should reach %s within %dms", expected, timeoutMs)
                .isEqualTo(expected);
    }

    private ConnectionManager newConnectionManager(String clientId, boolean fastRecoveryEnabled) {
        MqttIntegrationTestSupport support = MqttIntegrationTestSupport.getInstance();

        MqttClientProperties properties = new MqttClientProperties();
        properties.setEnable(false);
        properties.setInitClient(false);

        ServerInfo server = new ServerInfo();
        server.setHost(support.brokerHost());
        server.setPort(support.brokerPort());
        server.setDefaultNetworkType(NetworkTypeEnum.PUBLIC);
        server.setUsername("e2e-user");
        server.setPassword("e2e-secret");
        properties.setServer(server);

        properties.getFastRecovery().setEnabled(fastRecoveryEnabled);
        properties.getFastRecovery().setMaxFastReconnectAttempts(3);
        properties.getFastRecovery().setFastReconnectInterval(500L);
        properties.getFastRecovery().setKeepAliveInterval(30);

        return new ConnectionManager(properties, clientId);
    }
}
