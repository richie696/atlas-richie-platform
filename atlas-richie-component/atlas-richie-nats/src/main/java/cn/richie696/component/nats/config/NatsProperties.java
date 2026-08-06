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
package cn.richie696.component.nats.config;

import cn.richie696.component.nats.connection.NatsAuthConfigurator;
import cn.richie696.component.nats.enums.AuthType;
import io.nats.client.Options;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
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
@ConfigurationProperties(prefix = "platform.component.nats")
public class NatsProperties {

    /**
     * 组件总开关
     */
    private boolean enabled = true;

    /**
     * NATS 服务器地址，支持逗号分隔多地址
     */
    private String server = "nats://localhost:4222";

    /**
     * 鉴权配置块。根据 {@link Auth#getType()} 选择 NONE/TOKEN/USERNAME/NKEY/CREDENTIALS，
     * 对应字段（如 token/username/nkey/jwt）按需填充，未使用字段会被忽略。
     */
    private Auth auth = new Auth();

    /**
     * 客户端连接元数据与初始握手配置。包含连接名（服务端日志可见）、连接超时、
     * 优雅排空超时以及 inbox / UTF-8 subject 等开关。
     */
    private Connection connection = new Connection();

    /**
     * 自动重连策略：包含最大重连次数、指数退避基础等待、TLS/非 TLS 抖动量与
     * 重连期间可缓冲的字节数。整体策略映射到 jnats 的 {@code maxReconnects /
     * reconnectWait / reconnectJitter(Tls) / reconnectBufferSize}。
     */
    private Reconnect reconnect = new Reconnect();

    /**
     * PING 心跳探测：间隔与最大未应答数。达到上限时客户端判定连接失活并触发重连。
     */
    private Ping ping = new Ping();

    /**
     * TLS 加密配置块。可选两种模式：内置 OpenTLS（{@link Tls#isOpentls()}）或
     * 自定义 keystore/truststore；密钥库可空，缺失时使用 JVM 默认信任管理器。
     */
    private Tls tls = new Tls();

    /**
     * NATS 协议层行为开关：VERBOSE / PEDANTIC / HEADERS / NoResponders 等。
     * 默认与官方客户端一致，仅在需要兼容老服务器或调试协议时调整。
     */
    private Protocol protocol = new Protocol();

    /**
     * RPC 请求-响应相关配置：旧式请求格式、inbox 清理周期、无参重载的默认超时。
     * 业务侧调用的 {@code request(subject, payload, type)} 默认使用 {@link Request#getDefaultTimeout()}。
     */
    private Request request = new Request();

    /**
     * 出站消息队列容量与背压策略。在 Core NATS 推送过载时由该配置决定行为。
     */
    private Queue queue = new Queue();

    /**
     * 链路追踪总开关。开启后组件使用 {@code OpenTelemetryNatsTracingSupport} 注入 W3C
     * trace context。关闭后所有 publish/subscribe 不再产生 span，节省少量开销。
     */
    private Tracing tracing = new Tracing();

    /**
     * 跨消息头透传白名单。仅白名单中的 header 会在 publish/handle 之间复制，
     * 防止业务滥用 headers 携带过大或敏感数据。
     */
    private HeaderPropagation headerPropagation = new HeaderPropagation();

    /**
     * 消费幂等去重开关与存储介质。开启后 {@code NatsSubscriberFactory} 会在 handler
     * 完成后记录消息 ID TTL 毫秒内不接受重复消息。
     */
    private Idempotent idempotent = new Idempotent();

    /**
     * JetStream 总开关与 Stream/Consumer 声明入口。启用后组件会按 {@link JetStream#getStreams()}
     * 自动声明资源，并通过 {@link JetStream#getDlq()} 启用 DLQ advisory 重路由。
     */
    private JetStream jetstream = new JetStream();

    /**
     * 将全量配置转换为 jnats {@link Options.Builder}。
     *
     * <p>映射顺序固定为：服务器地址 → 鉴权 → 连接元数据 → 重连 → PING → 协议 → 请求 →
     * 出站队列 → TLS。各分组内的字段按 jnats builder 调用语义填充，{@code null} / 空白的
     * 可选字段保持 builder 默认值而不显式覆盖。</p>
     *
     * <p>为何不做深拷贝：本方法仅生成 {@code Options.Builder}，所有嵌套配置仍由本
     * {@code NatsProperties} 持有，不存在调用方持有的外部可变状态。</p>
     *
     * @return 配置完整的 {@link Options.Builder}，可直接调用 {@code build()} 创建连接选项
     * @throws IllegalStateException 当 TLS 配置加载失败（OpenTLS 算法缺失或 keystore/truststore 读取错误）时抛出
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

    /**
     * 根据 {@link Tls} 配置为 {@link Options.Builder} 注入 TLS 能力。
     *
     * <p>两条路径互斥：{@link Tls#isOpentls()} 走 jnats 内置 OpenTLS 引擎；
     * 否则走 {@link #buildSslContext()} 自定义 {@link SSLContext}。所有失败
     * （OpenTLS 算法缺失、keystore/truststore 损坏）均被包装为
     * {@link IllegalStateException}，由调用方决定是否降级。</p>
     *
     * @param builder 待注入 TLS 配置的 jnats Options Builder
     * @throws IllegalStateException OpenTLS 算法不可用或自定义 SSL 上下文构建失败
     */
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

    /**
     * 根据 keystore/truststore 配置构建 {@link SSLContext}。
     *
     * <p>为什么 keystore 与 truststore 都允许为 null：缺省时使用 JVM 默认
     * key/trust manager，行为与 {@link SSLContext#init(KeyManager[], TrustManager[], SecureRandom)}
     * 文档一致。该设计避免每套部署都强制挂载 truststore，配合公网 CA 时可直接信任
     * JVM cacerts 内的根证书。</p>
     *
     * @return 配置完成的 {@link SSLContext}，可直接传入 {@code Options.Builder.sslContext}
     * @throws Exception keystore 加载、KeyManagerFactory / TrustManagerFactory 初始化失败时抛出
     */
    private SSLContext buildSslContext() throws Exception {
        KeyManager[] keyManagers = null;
        if (tls.getKeystorePath() != null && !tls.getKeystorePath().isBlank()) {
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (var fis = new FileInputStream(tls.getKeystorePath())) {
                keyStore.load(fis, tls.getKeystorePassword() != null
                        ? tls.getKeystorePassword().toCharArray() : null);
            }
            var kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, tls.getKeystorePassword() != null
                    ? tls.getKeystorePassword().toCharArray() : null);
            keyManagers = kmf.getKeyManagers();
        }

        TrustManager[] trustManagers = null;
        if (tls.getTruststorePath() != null && !tls.getTruststorePath().isBlank()) {
            var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (var fis = new FileInputStream(tls.getTruststorePath())) {
                trustStore.load(fis, tls.getTruststorePassword() != null
                        ? tls.getTruststorePassword().toCharArray() : null);
            }
            var tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
        }

        var sslContext = SSLContext.getInstance("TLS");
        // null 使用 JVM 默认 key/trust manager，未配置自定义证书时仍可信任系统 CA。
        sslContext.init(keyManagers, trustManagers, null);
        return sslContext;
    }

    // ======================== 内部配置类 ========================

    /**
     * 鉴权配置块。
     *
     * <p>与 jnats 的 {@code AuthOptions} 1:1 对应：根据 {@code getType()} 选择鉴权方式后，
     * 只有对应字段生效，未使用字段会被 {@link cn.richie696.component.nats.connection.NatsAuthConfigurator}
     * 忽略。所有字段绑定配置前缀 {@code platform.component.nats.auth.<field>}。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class Auth {
        /**
         * 鉴权方式。默认 {@link AuthType#NONE}（匿名访问），仅在生产环境需访问 NATS 服务器时
         * 显式切换为 TOKEN / USERNAME / NKEY / CREDENTIALS。
         */
        private AuthType type = AuthType.NONE;

        /**
         * TOKEN 模式下的静态令牌。{@link #getType()} 为 {@link AuthType#TOKEN} 时生效。
         */
        private String token;

        /**
         * USERNAME/PASSWORD 模式的用户名。{@link #getType()} 为 {@link AuthType#USERPASS} 时生效。
         */
        private String username;

        /**
         * USERNAME/PASSWORD 模式的密码。建议通过环境变量或密钥管理注入，避免写入 yml 明文。
         */
        private String password;

        /**
         * NKEY 模式的公钥（Seed 解签后得到）。与 {@link #getSeed()} 配套使用，或单独提供公钥。
         */
        private String nkey;

        /**
         * CREDENTIALS 模式下的凭据文件路径，文件内同时包含 JWT 与 NKEY Seed。
         * 推荐用于生产环境，运维只需轮换文件而无需重启应用。
         */
        private String credentialsFile;

        /**
         * 直接注入的 JWT 字符串。与 {@link #getCredentialsFile()} 二选一；通常用于
         * CI/CD 等不便挂载文件系统的场景。
         */
        private String jwt;

        /**
         * 直接注入的 NKEY Seed 字符串。与 {@link #getJwt()} 配套使用，敏感性极高。
         */
        private String seed;
    }

    /**
     * 客户端连接元数据与初始握手配置。
     *
     * <p>除超时与 inbox 前缀外，其它字段主要影响 NATS 协议层的可选能力
     * （noEcho / noRandomize / UTF-8 subject）。所有字段绑定配置前缀
     * {@code platform.component.nats.connection.<field>}。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class Connection {
        /**
         * 客户端连接名。会出现在 NATS 服务器日志与监控中，便于定位来源实例，
         * 推荐设为 {@code ${spring.application.name}}。
         */
        private String name = "nats-client";

        /**
         * 初始 TCP 握手 / INFO 帧的超时。超时未连接则按 {@link Reconnect#isEnabled()} 决定是否重试。
         */
        private Duration connectionTimeout = Duration.ofSeconds(5);

        /**
         * 应用关闭时 {@code close()} / {@code drain()} 的最大等待时间。超过则强制断开，
         * 防止进程被 NATS 异步 IO 阻塞住无法退出。
         */
        private Duration drainTimeout = Duration.ofSeconds(30);

        /**
         * 是否禁止回显本连接发布的消息。开启后本客户端无法收到自己发出的消息，常用于
         * 同一连接既要发布又要订阅的场景以避免自循环。
         */
        private boolean noEcho = false;

        /**
         * 是否禁用客户端随机选择服务器。默认 false 表示由 jnats 随机化初始连接目标，
         * 开启后固定按 {@code server} 列表顺序连接（调试时有用）。
         */
        private boolean noRandomize = false;

        /**
         * RPC 应答 inbox 主题前缀。RPC 请求会被发布到 {@code <inboxPrefix>.<uuid>} 上，
         * 由本客户端监听回收。修改前缀可在多客户端共用 NATS 时减少主题冲突概率。
         */
        private String inboxPrefix = "_INBOX";

        /**
         * 是否启用 UTF-8 主题支持。开启后 subject 可包含 UTF-8 字符（部分老服务器不支持）。
         */
        private boolean supportUtf8Subjects = false;
    }

    /**
     * 自动重连策略配置。
     *
     * <p>映射到 jnats 的 {@code maxReconnects / reconnectWait / reconnectJitter(Tls) /
     * reconnectBufferSize}，整体策略遵循「指数退避 + 抖动 + 重连期间缓冲」。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class Reconnect {
        /**
         * 是否启用自动重连。关闭后连接断开即抛出错误，不缓冲后续消息，适合一次性任务类应用。
         */
        private boolean enabled = true;

        /**
         * 最大重连次数。{@code -1} 表示无限重连（默认），其它值表示达到上限后放弃。
         * 重连上限触达后 {@link cn.richie696.component.nats.exception.NatsConnectionException} 会冒泡。
         */
        private int maxReconnects = -1;

        /**
         * 重连基础等待时间。实际退避 = {@code reconnectWait * 2^attempt}，封顶 2 秒；
         * 用于分散多客户端同时重连对服务端的冲击。
         */
        private Duration reconnectWait = Duration.ofSeconds(2);

        /**
         * 非 TLS 通道每次重连等待的随机抖动量，避免多客户端形成重连雪崩。
         */
        private Duration jitter = Duration.ofMillis(100);

        /**
         * TLS 通道每次重连等待的随机抖动量，通常大于非 TLS 以应对 TLS 握手耗时。
         */
        private Duration jitterTls = Duration.ofSeconds(1);

        /**
         * 重连期间可缓冲的消息字节数。超过后旧消息被丢弃新消息也将被拒绝（除非
         * {@link Queue#isDiscardWhenFull()} 为 true）。默认 8 MiB 足够缓冲短时抖动。
         */
        private long bufferSize = 8_388_608L;
    }

    /**
     * PING 心跳探测配置。
     *
     * <p>客户端按 {@code getInterval()} 周期性发送 PING；服务端应在等长时间内返回 PONG。
     * 若连续 {@code getMaxOutstanding()} 次未收到 PONG 则判定连接失活并触发重连。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class Ping {
        /**
         * PING 心跳间隔。低于 NATS 服务器默认 2 分钟即可，弱网环境建议缩到 10~20 秒。
         */
        private Duration interval = Duration.ofSeconds(20);

        /**
         * 触发连接判定为失活前允许的最大未应答 PING 数。值越小越敏感，但弱网下可能误判。
         */
        private int maxOutstanding = 2;
    }

    /**
     * TLS 加密配置块。
     *
     * <p>提供两种模式：</p>
     * <ul>
     *   <li>{@code isOpentls()} = true — 走 jnats 内置 OpenTLS 引擎，无需 keystore，
     *       自动使用 JVM 信任链。适合客户端不需要 mTLS 双向认证、仅做传输加密的场景。</li>
     *   <li>默认 / 自定义 keystore + truststore — 走 {@link SSLContext}，
     *       支持 mTLS 与自签证书。缺失的 keystore/truststore 会回退到 JVM 默认信任管理器，
     *       因此只配服务端证书校验、不配客户端证书也能正常工作。</li>
     * </ul>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class Tls {
        /**
         * 是否启用 TLS。开启后 {@link cn.richie696.component.nats.config.NatsProperties#toOptionsBuilder()}
         * 会调用 {@code configureTls}，否则保持明文 NATS 协议。
         */
        private boolean enabled = false;

        /**
         * 是否使用 jnats 内置 OpenTLS 引擎。开启后其余 keystore/truststore 配置被忽略，
         * 由 OpenTLS 直接处理握手。
         */
        private boolean opentls = false;

        /**
         * 客户端证书库（PKCS12/JKS）路径。配置后启用 mTLS 客户端身份认证；留空则只做服务端认证。
         */
        private String keystorePath;

        /**
         * 客户端证书库密码。
         */
        private String keystorePassword;

        /**
         * 服务端 CA 证书库路径。留空时 {@link NatsProperties#buildSslContext()} 会向
         * {@link SSLContext} 传入 null TrustManager，由 JVM 使用 cacerts
         * 中的默认 CA 集合校验服务端证书，避免每个环境都强制提供 truststore。
         */
        private String truststorePath;

        /**
         * CA 证书库密码。
         */
        private String truststorePassword;
    }

    /**
     * NATS 协议层行为开关。
     *
     * <p>默认与 jnats 一致，仅在需要兼容老版本服务器（&lt; 2.2）或调试协议交互时调整。
     * 全部开启会显著放大协议消息量，调试完成后应恢复为 false。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class Protocol {
        /**
         * 是否启用 VERBOSE 模式。开启后每条 PUB / SUB 服务器回 {@code +OK}，主要用于协议调试。
         */
        private boolean verbose = false;

        /**
         * 是否启用 PEDANTIC 严格模式。开启后服务器会校验 subject 命名规范等细节。
         */
        private boolean pedantic = false;

        /**
         * 是否禁用 NATS Headers（{@code headers=true} 协议层能力）。关闭后所有 publish
         * 都会去掉 headers；与 {@link HeaderPropagation} 解耦，
         * 此开关对应的是协议能力，本组件默认启用。
         */
        private boolean noHeaders = false;

        /**
         * 是否禁用 NoResponders 错误。开启后向没有 responder 的 subject 请求时不再抛出
         * 404，便于对接老版本服务器。
         */
        private boolean noResponders = false;

        /**
         * 控制行（INFO/PUB 协议头）最大字节数。超过将被视为非法并断开连接。
         */
        private int maxControlLine = 4096;
    }

    /**
     * RPC 请求-响应相关配置。
     *
     * <p>{@link cn.richie696.component.nats.bus.NatsBus} / {@link cn.richie696.component.nats.bus.NatsEndpoint} 中的 RPC 调用
     * 均依赖这些参数：旧式请求格式、inbox 清理周期以及无参重载的默认超时。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class Request {
        /**
         * 是否使用旧式请求格式（不带 headers）。仅在对接 &lt; 2.2 版本 NATS 服务器时开启。
         */
        private boolean oldStyle = false;

        /**
         * RPC inbox 过期清理周期。客户端按此间隔回收超期未响应的 inbox subscription，
         * 防止长生命周期进程内存增长。
         */
        private Duration cleanupInterval = Duration.ofSeconds(5);

        /**
         * {@link cn.richie696.component.nats.bus.NatsBus#request(String, Object, Class)} 等无超时重载的默认超时。
         * 业务方应优先调用显式超重重载，关键路径不要依赖默认值。
         */
        private Duration defaultTimeout = Duration.ofSeconds(5);
    }

    /**
     * 出站消息队列容量与背压策略。
     *
     * <p>Core NATS 的出站发送基于内部有界队列。{@link cn.richie696.component.nats.connection.NatsConnectionManager} 的
     * 内部 dispatcher 在队列满时根据本配置决定阻塞或丢弃。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class Queue {
        /**
         * 出站队列容量上限。{@code -1} 表示不限制（依赖 JVM 内存），生产环境建议显式设值。
         */
        private int maxOutgoingMessages = -1;

        /**
         * 队列满时是否丢弃新消息而非阻塞 publisher。true 适合 fire-and-forget 业务，
         * false 适合必须保证送达的关键路径。
         */
        private boolean discardWhenFull = false;
    }

    /**
     * 链路追踪总开关。
     *
     * <p>开启后 publish / subscribe / RPC 都会被 {@code OpenTelemetryNatsTracingSupport}
     * 包裹为 span 并通过 W3C {@code traceparent} header 跨消息传播。关闭后仍保留
     * header 注入但不再产生 span，适合 tracing 组件未启用或追求极限吞吐的场景。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class Tracing {
        /**
         * 是否注入 OpenTelemetry 上下文。默认开启，要求调用方已注入 OTel SDK。
         */
        private boolean enabled = true;
    }

    /**
     * 跨消息头透传白名单。
     *
     * <p>由 {@link cn.richie696.component.nats.strategy.DefaultNatsHeaderInjector} 在
     * publish 时按本白名单从 MDC/RequestContext 复制到消息 header，并由
     * {@link cn.richie696.component.nats.strategy.DefaultNatsHeaderExtractor} 在消费端
     * 还原回 MDC。白名单机制防止业务滥用 headers 携带敏感数据。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class HeaderPropagation {
        /**
         * 是否启用 header 透传。关闭后 injector/extractor 使用空集合，相当于完全透传被禁用。
         */
        private boolean enabled = true;

        /**
         * 允许透传的 header 集合。默认仅透传平台级上下文（租户 / 时区 / 语言 / 金丝雀标签），
         * 业务方可按需追加自定义 header。
         */
        private Set<String> headers = Set.of(
                "x-tenant-id",
                "x-rd-request-timezone",
                "x-rd-request-language",
                "x-rd-canary-tag"
        );
    }

    /**
     * 消费幂等去重开关与存储介质。
     *
     * <p>由 {@link cn.richie696.component.nats.pipeline.NatsSubscriberFactory} 在 handler
     * 完成后记录消息 ID；TTL 内再次收到相同 ID 的消息会被直接 ack 跳过。仅针对
     * at-least-once 投递的 JetStream consumer 有意义。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class Idempotent {
        /**
         * 是否启用消费幂等去重。关闭后 {@code NatsSubscriberFactory} 不会读取
         * {@code NatsIdempotentChecker}，直接交给业务 handler。
         */
        private boolean enabled = false;

        /**
         * 存储介质。{@code memory} 用于单实例部署（重启会丢失去重状态）；
         * {@code redis} 用于多实例共享，依赖 {@code atlas-richie-cache} 在 classpath 中。
         */
        private String datasource = "memory";

        /**
         * 已处理消息 ID 的去重 TTL（毫秒）。需大于上游 producer 重试周期，避免误判。
         */
        private long ttl = 120_000L;
    }

    /**
     * JetStream 持久化与 Stream/Consumer 自动装配入口。
     *
     * <p>启用后 {@link cn.richie696.component.nats.connection.JetStreamManagementService}
     * 会按 {@code getStreams()} 顺序声明/更新 Stream 与 Consumer。{@code getDlq()} 用于
     * 启用 advisory 监听并在重试耗尽后将消息重路由到 DLQ stream。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class JetStream {
        /**
         * 是否启用 JetStream。关闭后所有 JetStream API 由 {@code NatsComponent} 返回空实现，
         * 整个组件退化为 Core NATS。
         */
        private boolean enabled = false;

        /**
         * 启动时是否自动声明 / 更新 Stream 与 Consumer。关闭后需要外部脚本手动运维。
         */
        private boolean autoProvision = true;

        /**
         * 需要自动声明的 Stream 列表（含其 Consumer 列表）。
         */
        private List<StreamDefinition> streams = new ArrayList<>();

        /**
         * DLQ 配置。开启后 advisory 消费者会监听所有 Stream 的 {@code js.consumer.delivery.term.*}，
         * 重路由到 {@link Dlq#getStreamNameSuffix()} 命名的 DLQ stream。
         */
        private Dlq dlq = new Dlq();
    }

    /**
     * DLQ(Dead Letter Queue)配置
     *
     * <p>与 JetStream 协同工作：当 JetStream consumer 重试耗尽时，将失败消息转发到 DLQ stream
     * 进行持久化与人工排查。本期仅暴露基础开关与命名规则，重路由/重投递逻辑见后续 Todo。</p>
     *
     * <p>所有字段绑定配置前缀 {@code platform.component.nats.jetstream.dlq.<field>}。</p>
     */
    @Data
    public static class Dlq {
        /**
         * 是否启用 DLQ 功能（opt-in 开关）
         *
         * <p>默认 {@code false}，需业务方显式开启。配置项：
         * {@code platform.component.nats.jetstream.dlq.enabled}。</p>
         */
        private boolean enabled = false;

        /**
         * DLQ stream 命名后缀
         *
         * <p>原 stream 名 + 此后缀 = DLQ stream 名。例如 {@code ORDERS} → {@code ORDERS-dlq}。
         * 默认 {@code "-dlq"}。配置项：
         * {@code platform.component.nats.jetstream.dlq.stream-name-suffix}。</p>
         */
        private String streamNameSuffix = "-dlq";

        /**
         * 内部 advisory stream 名
         *
         * <p>本期 advisory stream 由 NATS 自动管理，本字段留作未来扩展（例如自定义 advisory
         * 消费者或迁移到外部监控通道）。默认 {@code "NATS_DLQ_ADVISORY"}。配置项：
         * {@code platform.component.nats.jetstream.dlq.advisory-stream-name}。</p>
         */
        private String advisoryStreamName = "NATS_DLQ_ADVISORY";

        /**
         * 内部 advisory consumer 名
         *
         * <p>订阅 NATS advisory 主题（js.consumer.delivery.term.*）的 consumer 名，
         * 用于感知原 consumer 重试耗尽事件并触发 DLQ 重路由。默认 {@code "nats-dlq-advisory"}。
         * 配置项：{@code platform.component.nats.jetstream.dlq.advisory-consumer-name}。</p>
         */
        private String advisoryConsumerName = "nats-dlq-advisory";

        /**
         * DLQ subject 后缀
         *
         * <p>原 subject + 此后缀 = DLQ subject。例如 {@code orders.persistent} →
         * {@code orders.persistent.dlq}。默认 {@code ".dlq"}。配置项：
         * {@code platform.component.nats.jetstream.dlq.subject-suffix}。</p>
         */
        private String subjectSuffix = ".dlq";

        /**
         * HA 多 pod 去重 queue group
         *
         * <p>多实例部署时，同一 queue group 内只有一例消费 advisory 消息，避免重复重路由。
         * 默认 {@code "nats-dlq-workers"}。配置项：
         * {@code platform.component.nats.jetstream.dlq.queue-group}。</p>
         */
        private String queueGroup = "nats-dlq-workers";

        /**
         * advisory consumer 自身重试上限
         *
         * <p>advisory consumer 自身投递失败的最大重试次数，超过后将停止消费并触发告警，
         * 防止 DLQ 通道自身进入死循环。默认 {@code 5}。配置项：
         * {@code platform.component.nats.jetstream.dlq.advisory-max-deliver}。</p>
         */
        private long advisoryMaxDeliver = 5;
    }

    /**
     * JetStream Stream 声明。
     *
     * <p>字段与 jnats {@code StreamConfiguration.Builder} 1:1 对应。列表中的每个
     * {@link ConsumerDefinition} 会以同名前缀声明在该 Stream 下。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class StreamDefinition {
        /**
         * Stream 名（必填）。同一个 NATS 集群内唯一，启动时会校验是否已存在。
         */
        private String name;

        /**
         * 该 Stream 监听的 subject 列表。空列表将被 NATS 拒绝，必须至少包含一个 subject。
         */
        private List<String> subjects = new ArrayList<>();

        /**
         * 存储类型。{@code file}（持久化到磁盘）或 {@code memory}（内存，重启即丢）。
         */
        private String storageType = "file";

        /**
         * 保留策略：{@code limits} / {@code interest} / {@code workqueue} / {@code time}。
         */
        private String retention = "limits";

        /**
         * 消息最大存活时间。超过的消息按 {@link #getDiscard()} 策略被丢弃。
         */
        private Duration maxAge = Duration.ofDays(7);

        /**
         * Stream 总字节上限。{@code -1} 表示不限制。
         */
        private long maxBytes = -1;

        /**
         * Stream 总消息数上限。{@code -1} 表示不限制。
         */
        private long maxMessages = -1;

        /**
         * 单条消息大小上限（字节）。{@code -1} 表示不限制。
         */
        private long maxMessageSize = -1;

        /**
         * 副本数。生产环境建议 &ge; 3，单节点测试可保持 1。
         */
        private int numReplicas = 1;

        /**
         * 达到容量上限时的丢弃策略。{@code old} 丢弃最旧消息，{@code new} 拒绝新消息。
         */
        private String discard = "old";

        /**
         * 是否允许 Rollup。开启后 Stream 可被发布一次性全量快照并删除历史。
         */
        private boolean allowRollup = false;

        /**
         * 是否禁止通过 Admin API 删除。生产保护，避免误删。
         */
        private boolean denyDelete = false;

        /**
         * 该 Stream 下的 Consumer 列表。
         */
        private List<ConsumerDefinition> consumers = new ArrayList<>();
    }

    /**
     * JetStream Consumer 声明。
     *
     * <p>字段与 jnats {@code ConsumerConfiguration.Builder} 1:1 对应。
     * 本组件额外暴露 {@code getBackoff()} 与 {@code getNakDelay()} 用于分别控制
     * 服务端重投退避与客户端主动 NAK 延迟，以适配长耗时业务（如 LLM Agent 调用）。</p>
     *
     * @author richie696
     * @since 1.0.0
     */
    @Data
    public static class ConsumerDefinition {
        /**
         * Consumer 名。Stream 内唯一。
         */
        private String name;

        /**
         * 过滤 subject。留空表示订阅 Stream 所有 subjects。
         */
        private String filterSubject;

        /**
         * ACK 策略：{@code explicit} / {@code none} / {@code all}。
         */
        private String ackPolicy = "explicit";

        /**
         * ACK 超时。超过此时间未确认则服务端重投。
         */
        private Duration ackWait = Duration.ofSeconds(30);

        /**
         * 最大投递次数。超过后 advisory 触发 DLQ 重路由。
         */
        private int maxDeliver = 3;

        /**
         * 最大未确认积压。Agent Worker 池并发上限应与此对齐，防止过载堆积。
         */
        private int maxAckPending = 1000;

        /**
         * 最大等待拉取数。限制同时等待 pull 请求的客户端数量。
         */
        private int maxWaiting = 512;

        /**
         * 非活跃阈值。超期未投递则服务端自动清理 Consumer，节省集群元数据。
         */
        private Duration inactiveThreshold = Duration.ofMinutes(5);

        /**
         * 投递策略。{@code all} 从头开始；{@code last} 仅最新；{@code byStartSequence} /
         * {@code byStartTime} 从指定位置。
         */
        private String deliverPolicy = "all";

        /**
         * 重放策略。{@code instant} 立即重放；{@code replay-by-time} 按时间窗口重放。
         */
        private String replayPolicy = "instant";

        /**
         * 限速（消息/秒）。{@code 0} 表示无限速。
         */
        private long rateLimit = 0;

        /**
         * 采样率。{@code 0} 不采样，越大采样事件越频繁。
         */
        private int sampleFrequency = 0;

        /**
         * 未确认消息的服务端重投退避序列。配置后覆盖 {@link #getAckWait()}。
         */
        private List<Duration> backoff = new ArrayList<>();

        /**
         * 业务处理失败时主动 NAK 的延迟；避免模型限流或瞬时故障造成热循环。
         */
        private Duration nakDelay = Duration.ofSeconds(5);
    }
}
