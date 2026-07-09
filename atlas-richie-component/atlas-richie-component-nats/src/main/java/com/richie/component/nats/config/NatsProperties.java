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
package com.richie.component.nats.config;

import com.richie.component.nats.connection.NatsAuthConfigurator;
import com.richie.component.nats.enums.AuthType;
import io.nats.client.Options;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * NATS 组件全量配置属性
 *
 * <p>设计理念：暴露 jnats 原生驱动的全部配置能力，仅在以下情况隐藏：</p>
 * <ul>
 *   <li>组件内部已托管（如 errorListener / connectionListener / executor）</li>
 *   <li>仅用于测试的内部参数（如 bufferSize / dataPortType）</li>
 * </ul>
 *
 * <p>所有暴露项均提供组件默认值，使用者可零配置启动，按需覆盖。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.nats")
public class NatsProperties {

    /** 组件总开关 */
    private boolean enabled = true;

    /** NATS 服务器地址，支持逗号分隔多地址 */
    private String server = "nats://localhost:4222";

    private Auth auth = new Auth();
    private Connection connection = new Connection();
    private Reconnect reconnect = new Reconnect();
    private Ping ping = new Ping();
    private Tls tls = new Tls();
    private Protocol protocol = new Protocol();
    private Request request = new Request();
    private Queue queue = new Queue();
    private Tracing tracing = new Tracing();
    private HeaderPropagation headerPropagation = new HeaderPropagation();
    private Idempotent idempotent = new Idempotent();
    private Error error = new Error();
    private JetStream jetstream = new JetStream();

    @NestedConfigurationProperty
    private Dlq dlq = new Dlq();

    /**
     * 将全量配置转换为 jnats {@link Options.Builder}
     *
     * @return Options.Builder 可直接调用 build() 创建连接选项
     */
    public Options.Builder toOptionsBuilder() {
        var builder = new Options.Builder();

        // ===== Server =====
        String[] servers = server.split(",");
        if (servers.length == 1) {
            builder.server(servers[0].trim());
        } else {
            String[] trimmed = new String[servers.length];
            for (int i = 0; i < servers.length; i++) {
                trimmed[i] = servers[i].trim();
            }
            builder.servers(trimmed);
        }

        // ===== Auth =====
        new NatsAuthConfigurator().configure(builder, auth);

        // ===== Connection =====
        if (connection.getName() != null && !connection.getName().isBlank()) {
            builder.connectionName(connection.getName());
        }
        builder.connectionTimeout(connection.getConnectionTimeout());
        if (connection.isNoEcho()) {
            builder.noEcho();
        }
        if (connection.isNoRandomize()) {
            builder.noRandomize();
        }
        if (connection.getInboxPrefix() != null && !connection.getInboxPrefix().isBlank()) {
            builder.inboxPrefix(connection.getInboxPrefix());
        }
        if (connection.isSupportUtf8Subjects()) {
            builder.supportUTF8Subjects();
        }

        // ===== Reconnect =====
        if (!reconnect.isEnabled()) {
            builder.noReconnect();
        } else {
            builder.maxReconnects(reconnect.getMaxReconnects());
            builder.reconnectWait(reconnect.getReconnectWait());
            builder.reconnectJitter(reconnect.getJitter());
            builder.reconnectJitterTls(reconnect.getJitterTls());
            builder.reconnectBufferSize(reconnect.getBufferSize());
            // jnats 2.25.3 无 retryOnFailedConnect，通过 reconnectDelayHandler 可自定义策略
        }

        // ===== Ping =====
        builder.pingInterval(ping.getInterval());
        builder.maxPingsOut(ping.getMaxOutstanding());

        // ===== Protocol =====
        if (protocol.isVerbose()) {
            builder.verbose();
        }
        if (protocol.isPedantic()) {
            builder.pedantic();
        }
        if (protocol.isNoHeaders()) {
            builder.noHeaders();
        }
        if (protocol.isNoResponders()) {
            builder.noNoResponders();
        }
        builder.maxControlLine(protocol.getMaxControlLine());

        // ===== Request =====
        if (request.isOldStyle()) {
            builder.oldRequestStyle();
        }
        builder.requestCleanupInterval(request.getCleanupInterval());

        // ===== Queue =====
        builder.maxMessagesInOutgoingQueue(queue.getMaxOutgoingMessages());
        if (queue.isDiscardWhenFull()) {
            builder.discardMessagesWhenOutgoingQueueFull();
        }

        // ===== TLS =====
        if (tls.isEnabled()) {
            configureTls(builder);
        }

        return builder;
    }

    private void configureTls(Options.Builder builder) {
        if (tls.isOpentls()) {
            try {
                builder.opentls();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("Failed to configure OpenTLS for NATS connection", e);
            }
            return;
        }
        try {
            var sslContext = buildSslContext();
            builder.sslContext(sslContext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure TLS for NATS connection", e);
        }
    }

    private SSLContext buildSslContext() throws Exception {
        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        if (tls.getKeystorePath() != null && !tls.getKeystorePath().isBlank()) {
            try (var fis = new FileInputStream(tls.getKeystorePath())) {
                keyStore.load(fis, tls.getKeystorePassword() != null
                        ? tls.getKeystorePassword().toCharArray() : null);
            }
        }

        var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        if (tls.getTruststorePath() != null && !tls.getTruststorePath().isBlank()) {
            try (var fis = new FileInputStream(tls.getTruststorePath())) {
                trustStore.load(fis, tls.getTruststorePassword() != null
                        ? tls.getTruststorePassword().toCharArray() : null);
            }
        }

        var kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, tls.getKeystorePassword() != null
                ? tls.getKeystorePassword().toCharArray() : null);

        var tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    // ======================== 内部配置类 ========================

    @Data
    public static class Auth {
        private AuthType type = AuthType.NONE;
        private String token;
        private String username;
        private String password;
        private String nkey;
        private String credentialsFile;
        private String jwt;
        private String seed;
    }

    @Data
    public static class Connection {
        private String name = "nats-client";
        private Duration connectionTimeout = Duration.ofSeconds(5);
        private Duration drainTimeout = Duration.ofSeconds(30);
        private boolean noEcho = false;
        private boolean noRandomize = false;
        private String inboxPrefix = "_INBOX";
        private boolean supportUtf8Subjects = false;
    }

    @Data
    public static class Reconnect {
        private boolean enabled = true;
        private int maxReconnects = -1;
        private Duration reconnectWait = Duration.ofSeconds(2);
        private Duration jitter = Duration.ofMillis(100);
        private Duration jitterTls = Duration.ofSeconds(1);
        private long bufferSize = 8_388_608L;
        private boolean retryOnFailedConnect = false;
    }

    @Data
    public static class Ping {
        private Duration interval = Duration.ofSeconds(20);
        private int maxOutstanding = 2;
    }

    @Data
    public static class Tls {
        private boolean enabled = false;
        private boolean opentls = false;
        private String keystorePath;
        private String keystorePassword;
        private String truststorePath;
        private String truststorePassword;
    }

    @Data
    public static class Protocol {
        private boolean verbose = false;
        private boolean pedantic = false;
        private boolean noHeaders = false;
        private boolean noResponders = false;
        private int maxControlLine = 4096;
    }

    @Data
    public static class Request {
        private boolean oldStyle = false;
        private Duration cleanupInterval = Duration.ofSeconds(5);
        private Duration defaultTimeout = Duration.ofSeconds(5);
    }

    @Data
    public static class Queue {
        private int maxOutgoingMessages = -1;
        private boolean discardWhenFull = false;
    }

    @Data
    public static class Tracing {
        private boolean enabled = true;
    }

    @Data
    public static class HeaderPropagation {
        private boolean enabled = true;
        private Set<String> headers = Set.of(
                "x-tenant-id",
                "x-rd-request-timezone",
                "x-rd-request-language",
                "x-rd-canary-tag"
        );
    }

    @Data
    public static class Idempotent {
        private boolean enabled = false;
        private String datasource = "memory";
        private long ttl = 120_000L;
    }

    @Data
    public static class Error {
        private int maxRetries = 3;
        private Duration retryDelay = Duration.ofSeconds(1);
    }

    @Data
    public static class JetStream {
        private boolean enabled = false;
        private boolean autoProvision = true;
        private List<StreamDefinition> streams = new ArrayList<>();
    }

    /**
     * DLQ(Dead Letter Queue)配置
     *
     * <p>与 JetStream 协同工作：当 JetStream consumer 重试耗尽时，将失败消息转发到 DLQ stream
     * 进行持久化与人工排查。本期仅暴露基础开关与命名规则，重路由/重投递逻辑见后续 Todo。</p>
     *
     * <p>所有字段绑定配置前缀 {@code platform.nats.jetstream.dlq.<field>}，
     * 顶层 {@code @ConfigurationProperties(prefix="platform.nats")} 已包含 {@code jetstream} 路径。</p>
     */
    @Data
    public static class Dlq {
        /**
         * 是否启用 DLQ 功能（opt-in 开关）
         *
         * <p>默认 {@code false}，需业务方显式开启。配置项：
         * {@code platform.nats.jetstream.dlq.enabled}。</p>
         */
        private boolean enabled = false;

        /**
         * DLQ stream 命名后缀
         *
         * <p>原 stream 名 + 此后缀 = DLQ stream 名。例如 {@code ORDERS} → {@code ORDERS-dlq}。
         * 默认 {@code "-dlq"}。配置项：
         * {@code platform.nats.jetstream.dlq.stream-name-suffix}。</p>
         */
        private String streamNameSuffix = "-dlq";

        /**
         * 内部 advisory stream 名
         *
         * <p>本期 advisory stream 由 NATS 自动管理，本字段留作未来扩展（例如自定义 advisory
         * 消费者或迁移到外部监控通道）。默认 {@code "NATS_DLQ_ADVISORY"}。配置项：
         * {@code platform.nats.jetstream.dlq.advisory-stream-name}。</p>
         */
        private String advisoryStreamName = "NATS_DLQ_ADVISORY";

        /**
         * 内部 advisory consumer 名
         *
         * <p>订阅 NATS advisory 主题（js.consumer.delivery.term.*）的 consumer 名，
         * 用于感知原 consumer 重试耗尽事件并触发 DLQ 重路由。默认 {@code "nats-dlq-advisory"}。
         * 配置项：{@code platform.nats.jetstream.dlq.advisory-consumer-name}。</p>
         */
        private String advisoryConsumerName = "nats-dlq-advisory";

        /**
         * DLQ subject 后缀
         *
         * <p>原 subject + 此后缀 = DLQ subject。例如 {@code orders.persistent} →
         * {@code orders.persistent.dlq}。默认 {@code ".dlq"}。配置项：
         * {@code platform.nats.jetstream.dlq.subject-suffix}。</p>
         */
        private String subjectSuffix = ".dlq";

        /**
         * HA 多 pod 去重 queue group
         *
         * <p>多实例部署时，同一 queue group 内只有一例消费 advisory 消息，避免重复重路由。
         * 默认 {@code "nats-dlq-workers"}。配置项：
         * {@code platform.nats.jetstream.dlq.queue-group}。</p>
         */
        private String queueGroup = "nats-dlq-workers";

        /**
         * advisory consumer 自身重试上限
         *
         * <p>advisory consumer 自身投递失败的最大重试次数，超过后将停止消费并触发告警，
         * 防止 DLQ 通道自身进入死循环。默认 {@code 5}。配置项：
         * {@code platform.nats.jetstream.dlq.advisory-max-deliver}。</p>
         */
        private long advisoryMaxDeliver = 5;
    }

    @Data
    public static class StreamDefinition {
        private String name;
        private List<String> subjects = new ArrayList<>();
        private String storageType = "file";
        private String retention = "limits";
        private Duration maxAge = Duration.ofDays(7);
        private long maxBytes = -1;
        private long maxMessages = -1;
        private long maxMessageSize = -1;
        private int numReplicas = 1;
        private String discard = "old";
        private boolean allowRollup = false;
        private boolean denyDelete = false;
        private List<ConsumerDefinition> consumers = new ArrayList<>();
    }

    @Data
    public static class ConsumerDefinition {
        private String name;
        private String filterSubject;
        private String ackPolicy = "explicit";
        private Duration ackWait = Duration.ofSeconds(30);
        private int maxDeliver = 3;
        private int maxAckPending = 1000;
        private int maxWaiting = 512;
        private Duration inactiveThreshold = Duration.ofMinutes(5);
        private String deliverPolicy = "all";
        private String replayPolicy = "instant";
        private long rateLimit = 0;
        private int sampleFrequency = 0;
    }
}
