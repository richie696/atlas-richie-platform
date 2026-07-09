/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.nats;

import com.richie.component.nats.bus.NatsBus;
import com.richie.component.nats.config.NatsProperties;
import com.richie.component.nats.connection.NatsConnectionManager;
import com.richie.component.nats.bus.NatsEndpoint;
import com.richie.component.nats.enums.ConnectionState;
import com.richie.component.nats.connection.JetStreamManagementService;
import com.richie.component.nats.bus.JetStreamBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

/**
 * NATS 组件统一门面 + 生命周期管理
 *
 * <p>实现 {@link SmartLifecycle}，在 Spring 容器启动时初始化连接、声明 Stream/Consumer，
 * 在容器关闭时优雅 drain 所有订阅并关闭连接。</p>
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * @Autowired NatsComponent nats;
 *
 * nats.bus().publish("subject", message);       // Core NATS
 * nats.stream().publish("stream", "subject", message);  // JetStream
 * nats.endpoint().registerHandler("subject", ReqType.class, req -> resp);  // RPC
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class NatsComponent implements SmartLifecycle {

    private final NatsProperties properties;
    private final NatsConnectionManager connectionManager;
    private final JetStreamManagementService jetStreamManagementService;
    private final NatsBus natsBus;
    private final JetStreamBus jetStreamBus;
    private final NatsEndpoint natsEndpoint;

    private volatile boolean running = false;

    public NatsComponent(NatsProperties properties,
                         NatsConnectionManager connectionManager,
                         JetStreamManagementService jetStreamManagementService,
                         NatsBus natsBus,
                         JetStreamBus jetStreamBus,
                         NatsEndpoint natsEndpoint) {
        this.properties = properties;
        this.connectionManager = connectionManager;
        this.jetStreamManagementService = jetStreamManagementService;
        this.natsBus = natsBus;
        this.jetStreamBus = jetStreamBus;
        this.natsEndpoint = natsEndpoint;
    }

    // ===== 协议域入口（选类即选协议）=====

    /**
     * Core NATS 门面（fire-and-forget 发布 + 订阅 + RPC 请求）
     */
    public NatsBus bus() {
        return natsBus;
    }

    /**
     * JetStream 门面（持久化发布 + 消费 + 拉取）
     */
    public JetStreamBus stream() {
        return jetStreamBus;
    }

    /**
     * RPC 端点注册
     */
    public NatsEndpoint endpoint() {
        return natsEndpoint;
    }

    // ===== 连接状态查询 =====

    /**
     * 获取当前连接状态
     */
    public ConnectionState getState() {
        return connectionManager.getState();
    }

    /**
     * 获取连接管理器（高级用法）
     */
    public NatsConnectionManager getConnectionManager() {
        return connectionManager;
    }

    // ===== SmartLifecycle =====

    @Override
    public void start() {
        log.info("NATS component starting...");

        // 1. 初始化连接
        connectionManager.getConnection();
        log.info("NATS connection established, state: {}", connectionManager.getState());

        // 2. JetStream Stream/Consumer 声明
        if (properties.getJetstream().isEnabled()) {
            // 新 overload: 同时 provision 业务 stream + DLQ stream (R-Stream 命名 + R-HA queue group)
            jetStreamManagementService.provisionAll(properties);
            log.info("NATS JetStream streams/consumers provisioned");
        }

        running = true;
        log.info("NATS component started successfully");
    }

    @Override
    public void stop() {
        log.info("NATS component stopping...");
        connectionManager.shutdown(properties.getConnection().getDrainTimeout());
        running = false;
        log.info("NATS component stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 较早启动（基础设施），较晚关闭
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isEnabled();
    }
}
