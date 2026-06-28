package com.richie.component.nats.integration;

import com.richie.component.nats.support.NatsIntegrationTestSupport;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * NATS Server 连接集成测试 — 使用 Testcontainers 启动真实 NATS 容器。
 * <p>
 * 验证 jnats 客户端可以连接到 Testcontainers 启动的 NATS 服务器，
 * 并完成基本的连接状态查询。
 *
 * @author richie696
 */
@EnabledIf("com.richie.component.nats.support.NatsIntegrationTestSupport#isEnabled")
class NatsServerConnectivityIT {

    @Test
    void shouldConnectToTestcontainerNatsServer() throws Exception {
        NatsIntegrationTestSupport support = NatsIntegrationTestSupport.getInstance();

        Connection conn = Nats.connect(support.connectionUrl());
        try {
            assertThat(conn.getStatus()).isEqualTo(Connection.Status.CONNECTED);
            assertThat(conn.getServerInfo()).isNotNull();
            assertThat(conn.getServerInfo().getVersion()).isNotBlank();
        } finally {
            conn.close();
        }
    }

    @Test
    void shouldConnectWithOptionsBuilder() throws Exception {
        NatsIntegrationTestSupport support = NatsIntegrationTestSupport.getInstance();

        Options options = new Options.Builder()
                .server(support.connectionUrl())
                .connectionTimeout(Duration.ofSeconds(5))
                .maxReconnects(0)
                .build();

        Connection conn = Nats.connect(options);
        try {
            assertThat(conn.getStatus()).isEqualTo(Connection.Status.CONNECTED);
        } finally {
            conn.close();
        }
    }

    @Test
    void shouldConnectAndDisconnectGracefully() {
        NatsIntegrationTestSupport support = NatsIntegrationTestSupport.getInstance();

        assertThatCode(() -> {
            Connection conn = Nats.connect(support.connectionUrl());
            conn.drain(Duration.ofSeconds(5));
        }).doesNotThrowAnyException();
    }
}
