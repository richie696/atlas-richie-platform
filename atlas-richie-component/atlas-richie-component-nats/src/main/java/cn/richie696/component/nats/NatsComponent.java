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
package cn.richie696.component.nats;

import cn.richie696.component.nats.bus.JetStreamBus;
import cn.richie696.component.nats.bus.NatsBus;
import cn.richie696.component.nats.bus.NatsEndpoint;
import cn.richie696.component.nats.config.NatsProperties;
import cn.richie696.component.nats.connection.JetStreamManagementService;
import cn.richie696.component.nats.connection.NatsConnectionManager;
import cn.richie696.component.nats.enums.ConnectionState;
import cn.richie696.component.nats.exception.NatsException;
import io.nats.client.KeyValue;
import io.nats.client.ObjectStore;
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
 * <p><b>线程安全</b>：实例由 Spring 单例持有；{@code running} 使用 {@code volatile} 修饰保证可见性，
 * 业务方法均可被多线程并发调用。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class NatsComponent implements SmartLifecycle {

    /** 外部配置，决定是否启用组件以及 Stream/Consumer/DLQ 拓扑。 */
    private final NatsProperties properties;
    /** NATS 连接持有者，负责连接的建立、重连与 drain。 */
    private final NatsConnectionManager connectionManager;
    /** Stream / Consumer / DLQ 资源的幂等声明服务。 */
    private final JetStreamManagementService jetStreamManagementService;
    /** Core NATS 门面：fire-and-forget 发布与订阅。 */
    private final NatsBus natsBus;
    /** JetStream 门面：持久化发布与消费。 */
    private final JetStreamBus jetStreamBus;
    /** RPC 端点注册门面。 */
    private final NatsEndpoint natsEndpoint;

    /** 生命周期标志位，{@code volatile} 保证 {@link #isRunning()} 在多线程下的可见性。 */
    private volatile boolean running = false;

    /**
     * 构造 NATS 组件门面实例。所有依赖由 Spring 注入，构造过程不建立连接，副作用延后到 {@link #start()}。
     *
     * @param properties                 NATS 外部配置
     * @param connectionManager          连接持有者
     * @param jetStreamManagementService JetStream 资源声明服务
     * @param natsBus                    Core NATS 门面
     * @param jetStreamBus               JetStream 门面
     * @param natsEndpoint               RPC 端点注册门面
     */
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
     * 获取 Core NATS 门面（fire-and-forget 发布 + 订阅 + RPC 请求）。
     * <p>调用方按需选择协议域：Core NATS 适合低延迟、不要求持久化的场景。</p>
     *
     * @return Core NATS 门面实例
     */
    public NatsBus bus() {
        return natsBus;
    }

    /**
     * 获取 JetStream 门面（持久化发布 + 消费 + 拉取）。
     * <p>适用于需要 at-least-once 投递与回溯消费的业务流。</p>
     *
     * @return JetStream 门面实例
     */
    public JetStreamBus stream() {
        return jetStreamBus;
    }

    /**
     * 获取 RPC 端点注册门面。
     * <p>用于把本地服务方法注册为基于 Core NATS Request-Reply 的远程过程。</p>
     *
     * @return RPC 端点门面实例
     */
    public NatsEndpoint endpoint() {
        return natsEndpoint;
    }

    /**
     * 获取已有的 JetStream Key-Value bucket。
     * <p>仅访问已由 {@link JetStreamManagementService} 预声明的 bucket；
     * 若 bucket 不存在或连接不可用，所有底层异常会被包装为 {@link NatsException} 抛出，
     * 以保持统一的异常口径。</p>
     *
     * @param bucketName bucket 名称
     * @return NATS {@link KeyValue} bucket 句柄
     * @throws NatsException 当底层连接或 bucket 访问失败时抛出
     */
    public KeyValue keyValue(String bucketName) {
        try {
            return connectionManager.getConnection().keyValue(bucketName);
        } catch (Exception e) {
            throw new NatsException("Failed to access NATS Key-Value bucket: " + bucketName, e);
        }
    }

    /**
     * 获取已有的 JetStream Object Store bucket。
     * <p>仅访问已声明的 bucket；底层异常统一包装为 {@link NatsException}，避免上层感知 JetStream SDK。</p>
     *
     * @param bucketName bucket 名称
     * @return NATS {@link ObjectStore} bucket 句柄
     * @throws NatsException 当底层连接或 bucket 访问失败时抛出
     */
    public ObjectStore objectStore(String bucketName) {
        try {
            return connectionManager.getConnection().objectStore(bucketName);
        } catch (Exception e) {
            throw new NatsException("Failed to access NATS Object Store bucket: " + bucketName, e);
        }
    }

    // ===== 连接状态查询 =====

    /**
     * 获取当前 NATS 连接状态，便于健康检查与告警。
     *
     * @return 当前连接状态
     */
    public ConnectionState getState() {
        return connectionManager.getState();
    }

    /**
     * 获取底层连接管理器（高级用法）。
     * <p>仅在需要直接访问 {@link NatsConnectionManager}（例如自定义连接配置或诊断）时使用，
     * 一般业务应优先通过 {@link #bus()} / {@link #stream()} / {@link #endpoint()} 操作。</p>
     *
     * @return 连接管理器实例
     */
    public NatsConnectionManager getConnectionManager() {
        return connectionManager;
    }

    // ===== SmartLifecycle =====

    /**
     * Spring 容器启动时触发：先建立连接，再按需声明 JetStream 资源。
     * <p>该方法遵循连接优先原则——只有连接建立成功后才能拿到
     * {@link io.nats.client.JetStream} 上下文；Stream/Consumer 声明失败会冒泡，便于
     * 平台启动期快速失败而非让业务在缺少资源的情况下静默运行。</p>
     */
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

    /**
     * Spring 容器关闭时触发：按配置的 drain 超时优雅等待，未在窗口内完成的订阅将被强中断。
     * <p>先把 {@code running} 置位用于拦截新请求，再调用 {@link NatsConnectionManager#shutdown(Duration)}
     * 完成 drain + 关闭，避免半关闭状态导致下游调用阻塞。</p>
     */
    @Override
    public void stop() {
        log.info("NATS component stopping...");
        connectionManager.shutdown(properties.getConnection().getDrainTimeout());
        running = false;
        log.info("NATS component stopped");
    }

    /**
     * 判断组件是否处于运行态，供 Spring 生命周期与探针复用。
     *
     * @return {@code true} 表示 {@link #start()} 已完成且未进入 {@link #stop()}
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * SmartLifecycle 阶段值。返回较大值以保证 NATS 作为基础设施先于业务 Bean 启动、晚于业务关闭，
     * 使业务消费者在订阅建立前已完成初始化。
     *
     * @return 生命周期阶段，固定为 {@link Integer#MAX_VALUE} - 100
     */
    @Override
    public int getPhase() {
        // 较早启动（基础设施），较晚关闭
        return Integer.MAX_VALUE - 100;
    }

    /**
     * 是否随 Spring 容器自动启动：由 {@code platform.component.nats.enabled} 配置控制，
     * 关闭时组件不会创建连接，便于测试或按需启用。
     *
     * @return 配置启用时返回 {@code true}
     */
    @Override
    public boolean isAutoStartup() {
        return properties.isEnabled();
    }
}
