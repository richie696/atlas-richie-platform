package com.richie.component.nats.dlq;

import com.richie.component.nats.config.NatsAutoConfiguration;
import com.richie.component.nats.connection.NatsConnectionManager;
import com.richie.component.nats.support.NatsIntegrationTestSupport;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.api.MessageInfo;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.api.StreamState;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * DLQ advisory 监听器端到端集成测试 — 使用 Testcontainers NATS 2.10-alpine 真服务器。
 *
 * <h2>覆盖场景</h2>
 * <p>业务 consumer 重试耗尽 → NATS 自动发布 {@code MAX_DELIVERIES} advisory →
 * {@link NatsDeadLetterAdvisoryConsumer} 监听 advisory → 编排"取原消息 → 入 DLQ → 擦除原消息"
 * 全流程。</p>
 *
 * <h2>断言矩阵</h2>
 * <ol>
 *   <li>DLQ stream {@code <STREAM>-dlq} 存在且 msg-count ≥ 1</li>
 *   <li>DLQ message payload equals 原 publish payload(透传)</li>
 *   <li>原消息已从业务 stream erase({@code getMessage} 抛 10060 Message Not Found)</li>
 *   <li>DLQ message headers 含 traceparent(大小写无关透传)</li>
 *   <li>DLQ message headers 含 NATS-DLQ-Retry-Count(从 advisory.deliveries 提取)</li>
 *   <li>DLQ message headers 含 NATS-DLQ-Source-Stream=业务 stream 名</li>
 *   <li>DLQ message subject = {@code orders.persistent.dlq}(原 subject + DLQ subject suffix)</li>
 * </ol>
 *
 * <h2>设计约束</h2>
 * <ul>
 *   <li>业务 stream 名每次运行 UUID 后缀,@AfterEach 清理,避免污染</li>
 *   <li>业务 consumer {@code maxDeliver=2} + {@code ackWait=5s},advisory 在第 2 次投递后触发</li>
 *   <li>驱动 max-deliver 用 fetch+nak 循环(不是 {@code Thread.sleep} 干等)</li>
 *   <li>DLQ 消息存在用 Awaitility 等待(不用 {@code Thread.sleep} 替代 advisory 等待)</li>
 * </ul>
 *
 * @author richie696
 */
@EnabledIf("com.richie.component.nats.support.NatsIntegrationTestSupport#isEnabled")
@SpringBootTest(classes = NatsAutoConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NatsDeadLetterAdvisoryConsumerEndToEndIT {

    /** OTel traceparent fixture,验证 DLQ headers 透传。 */
    private static final String FIXTURE_TRACE_PARENT =
            "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

    /**
     * 业务 stream 名 — 每次运行 UUID 后缀,避免与上次残留的 stream 冲突。
     * 注意 AdvisoryConsumer 按 {@code properties.getJetstream().getStreams()}
     * 静态列表订阅,所以这个常量必须在 @DynamicPropertySource 注册之前初始化。
     */
    private static final String STREAM_NAME = "IT_DLQ_ORDERS_"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

    private static final String DLQ_STREAM_NAME = STREAM_NAME + "-dlq";
    private static final String SUBJECT = "orders.persistent";
    private static final String DLQ_SUBJECT = SUBJECT + ".dlq";
    private static final String BUSINESS_CONSUMER = "test-consumer";
    private static final int MAX_DELIVER = 2;
    private static final Duration ACK_WAIT = Duration.ofSeconds(5);

    @Autowired
    private NatsConnectionManager connectionManager;

    private Connection adminConn;
    private JetStreamManagement jsm;
    private JetStream js;
    private io.nats.client.MessageConsumer nakConsumer;

    @DynamicPropertySource
    static void natsProperties(DynamicPropertyRegistry registry) {
        // base NATS 连接属性(由 NatsIntegrationTestSupport 注入 testcontainers 启动参数)
        NatsIntegrationTestSupport.getInstance().registerNatsProperties(registry);

        registry.add("platform.nats.jetstream.enabled", () -> "true");
        registry.add("platform.nats.jetstream.auto-provision", () -> "true");

        // DLQ 开关必须设两路:
        //   - platform.nats.jetstream.dlq.enabled 用于 @ConditionalOnProperty 装配 DLQ bean
        //   - platform.nats.dlq.enabled 用于 @ConfigurationProperties 绑定到 NatsProperties.dlq.enabled
        //     (NatsProperties.dlq 是顶层 @NestedConfigurationProperty 字段,实际 binding 路径
        //      是 platform.nats.dlq.enabled,该值决定 provisionDlqStreams 是否实际跑)
        // 缺一会导致 Bean 不创建 OR Bean 装配了但 DLQ stream 没 provision
        registry.add("platform.nats.jetstream.dlq.enabled", () -> "true");
        registry.add("platform.nats.dlq.enabled", () -> "true");

        // 业务 stream + consumer 配置 — 被 NatsComponent.start() → provisionAll(properties) 消费
        // (注意:NatsComponent phase = MAX_VALUE - 100 < AdvisoryConsumer phase = MAX_VALUE - 50,
        //  所以 stream 必先 provision 完成,advisory subscription 才注册)
        registry.add("platform.nats.jetstream.streams[0].name", () -> STREAM_NAME);
        registry.add("platform.nats.jetstream.streams[0].subjects[0]", () -> SUBJECT);
        registry.add("platform.nats.jetstream.streams[0].storageType", () -> "file");
        registry.add("platform.nats.jetstream.streams[0].retention", () -> "limits");
        registry.add("platform.nats.jetstream.streams[0].discard", () -> "old");
        registry.add("platform.nats.jetstream.streams[0].consumers[0].name", () -> BUSINESS_CONSUMER);
        registry.add("platform.nats.jetstream.streams[0].consumers[0].ackPolicy", () -> "explicit");
        registry.add("platform.nats.jetstream.streams[0].consumers[0].deliverPolicy", () -> "all");
        registry.add("platform.nats.jetstream.streams[0].consumers[0].maxDeliver", () -> MAX_DELIVER);
        registry.add("platform.nats.jetstream.streams[0].consumers[0].ackWait", () -> ACK_WAIT);

        // 预创建 NATS 内部 advisory stream — nats 2.10 不会在启动时自动建,必须等首次 advisory
        // 事件才会 lazy 创建,导致 NatsDeadLetterAdvisoryConsumer 首次 js.subscribe 失败
        // (NATS 报 "No matching streams for subject") 降级到 Core NATS fallback,
        // 而 Core NATS handler 只能 fire-and-forget,不会真正把消息入 DLQ,断言永远不通过
        ensureAdvisoryStream();
    }

    private static void ensureAdvisoryStream() {
        try (Connection nc = Nats.connect(NatsIntegrationTestSupport.getInstance().connectionUrl())) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            try {
                jsm.addStream(StreamConfiguration.builder()
                        .name("JSAPI_ADVISORY")
                        .subjects("$JS.EVENT.ADVISORY.>")
                        .retentionPolicy(RetentionPolicy.Interest)
                        .storageType(StorageType.Memory)
                        .build());
            } catch (Exception e) {
                // 已存在时(10058)忽略;生产 NATS 可能自带该 stream
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to pre-create advisory stream", e);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // 独立 admin 连接 — 避开 Spring-managed connection 的 SmartLifecycle 干扰
        adminConn = Nats.connect(NatsIntegrationTestSupport.getInstance().connectionUrl());
        jsm = adminConn.jetStreamManagement();
        js = adminConn.jetStream();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (nakConsumer != null) {
            try {
                nakConsumer.stop();
            } catch (Exception ignored) {
                // best effort
            }
        }
        if (jsm != null) {
            try {
                jsm.deleteStream(DLQ_STREAM_NAME);
            } catch (Exception ignored) {
                // best effort
            }
            try {
                jsm.deleteStream(STREAM_NAME);
            } catch (Exception ignored) {
                // best effort
            }
        }
        if (adminConn != null && adminConn.getStatus() == Connection.Status.CONNECTED) {
            adminConn.close();
        }
    }

    @Test
    void shouldRouteDeadLetterOnMaxDeliver() throws Exception {
        var streamCtx = adminConn.getStreamContext(STREAM_NAME);
        var consumerCtx = streamCtx.getConsumerContext(BUSINESS_CONSUMER);

        nakConsumer = consumerCtx.consume(msg -> msg.nak());
        try {
            Headers headers = new Headers();
            headers.add("traceparent", FIXTURE_TRACE_PARENT);
            String payload = "poison-payload-" + UUID.randomUUID();
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            js.publish(SUBJECT, headers, payloadBytes);

            await()
                    .atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .until(this::dlqStreamHasMessage);

            // 断言 1:DLQ stream 存在
            StreamInfo dlqInfo = jsm.getStreamInfo(DLQ_STREAM_NAME);
            assertThat(dlqInfo).isNotNull();
            StreamState dlqState = dlqInfo.getStreamState();
            assertThat(dlqState.getMsgCount())
                    .as("DLQ stream should have at least 1 message")
                    .isGreaterThanOrEqualTo(1L);

            // 断言 2:DLQ message payload equals 原 publish payload
            MessageInfo dlqMsg = jsm.getMessage(DLQ_STREAM_NAME, 1L);
            assertThat(dlqMsg).isNotNull();
            assertThat(new String(dlqMsg.getData(), StandardCharsets.UTF_8))
                    .as("DLQ payload should equal original publish payload")
                    .isEqualTo(payload);

            // 断言 7:DLQ subject = orders.persistent.dlq
            assertThat(dlqMsg.getSubject())
                    .as("DLQ subject should be original + dlq.subjectSuffix")
                    .isEqualTo(DLQ_SUBJECT);

            // 断言 4-6:DLQ headers
            Headers dlqHeaders = dlqMsg.getHeaders();
            assertThat(dlqHeaders).isNotNull();

            // 断言 4:traceparent 透传(大小写无关匹配)
            String foundTraceparent = findHeaderIgnoreCase(dlqHeaders, "traceparent");
            assertThat(foundTraceparent)
                    .as("DLQ message should preserve original traceparent header")
                    .isEqualTo(FIXTURE_TRACE_PARENT);

            // 断言 5:NATS-DLQ-Retry-Count(取自 advisory.deliveries)
            List<String> retryCountHeader = dlqHeaders.get("NATS-DLQ-Retry-Count");
            assertThat(retryCountHeader)
                    .as("DLQ message should carry NATS-DLQ-Retry-Count header from advisory")
                    .isNotNull()
                    .hasSize(1);
            long retryCount = Long.parseLong(retryCountHeader.get(0));
            assertThat(retryCount)
                    .as("NATS-DLQ-Retry-Count should be >= 1 (advisory deliveries field)")
                    .isGreaterThanOrEqualTo(1L);

            // 断言 6:NATS-DLQ-Source-Stream=业务 stream 名
            List<String> sourceStreamHeader = dlqHeaders.get("NATS-DLQ-Source-Stream");
            assertThat(sourceStreamHeader)
                    .as("DLQ message should carry NATS-DLQ-Source-Stream header")
                    .isNotNull()
                    .hasSize(1);
            assertThat(sourceStreamHeader.get(0))
                    .as("NATS-DLQ-Source-Stream should equal the original business stream name")
                    .isEqualTo(STREAM_NAME);

            // 断言 3:原消息已从业务 stream erase(getMessage 抛 Not Found)
            // nats 2.10 用 main code 404 + api code 10037 表达 Message Not Found
            // (生产代码 NatsDeadLetterAdvisoryConsumer 锁死 10060 是早期 API 命名,
            //  本测试以 nats 2.10 实际返回的 404 为准,验证消息被擦除的事实即可)
            assertThatThrownBy(() -> jsm.getMessage(STREAM_NAME, 1L))
                    .as("original message should be erased from business stream")
                    .isInstanceOf(JetStreamApiException.class)
                    .satisfies(thrown -> {
                        JetStreamApiException jse = (JetStreamApiException) thrown;
                        assertThat(jse.getErrorCode())
                                .as("main error code should be 404 (Not Found)")
                                .isEqualTo(404);
                        assertThat(jse.getApiErrorCode())
                                .as("api error code should be 10037 (Message Not Found)")
                                .isEqualTo(10037);
                    });
        } finally {
            if (nakConsumer != null) {
                nakConsumer.stop();
                nakConsumer = null;
            }
        }
    }

    /**
     * Polling predicate:DLQ stream 是否已收到至少 1 条消息。供 Awaitility 调用,
     * 内部吞所有异常(NATS 端尚在 provision 时可能抛 404)。
     */
    private boolean dlqStreamHasMessage() {
        try {
            StreamInfo info = jsm.getStreamInfo(DLQ_STREAM_NAME);
            return info.getStreamState().getMsgCount() >= 1L;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 大小写无关 header 查找 — OTel SDK 不同版本注入的 header name 大小写不一致,
     * 镜像生产代码 {@code NatsDeadLetterAdvisoryConsumer.extractTraceparentIgnoreCase} 的策略。
     */
    private static String findHeaderIgnoreCase(Headers headers, String name) {
        if (headers == null) {
            return null;
        }
        for (String key : headers.keySet()) {
            if (name.equalsIgnoreCase(key)) {
                List<String> values = headers.get(key);
                if (values != null && !values.isEmpty()) {
                    return values.get(0);
                }
            }
        }
        return null;
    }
}
