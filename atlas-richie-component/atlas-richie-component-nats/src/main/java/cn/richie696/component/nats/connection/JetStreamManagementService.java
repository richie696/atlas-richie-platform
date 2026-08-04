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
package cn.richie696.component.nats.connection;

import cn.richie696.component.nats.config.NatsProperties;
import cn.richie696.component.nats.exception.NatsException;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.*;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.time.Duration;
import java.util.stream.Stream;

/**
 * JetStream 管理服务
 *
 * <p>负责启动时幂等声明 Stream 和 Consumer 定义。
 * 使用 {@link io.nats.client.JetStreamManagement} 进行 Stream/Consumer 的创建与更新。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class JetStreamManagementService {

    private final NatsConnectionManager connectionManager;

    public JetStreamManagementService(NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * 幂等确保 Stream 存在（不存在则创建，已存在则跳过）。
     *
     * <p>判断依据为 jnats {@code 404 Not Found}：{@code getStreamInfo} 抛 404 才走 addStream 分支；
     * 其它异常向上抛出，由调用方决定是否重试。</p>
     *
     * @param def Stream 定义
     * @throws NatsException 当连接失败或 jnats 在 addStream 过程中抛非 404 异常时
     */
    public void ensureStreamExists(NatsProperties.StreamDefinition def) {
        try {
            var mgmt = connectionManager.getConnection().jetStreamManagement();
            try {
                mgmt.getStreamInfo(def.getName());
                log.info("JetStream stream [{}] already exists, skipping creation", def.getName());
            } catch (JetStreamApiException e) {
                if (e.getErrorCode() == 404) {
                    var config = buildStreamConfiguration(def);
                    mgmt.addStream(config);
                    log.info("JetStream stream [{}] created successfully", def.getName());
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new NatsException("Failed to ensure stream exists: " + def.getName(), e);
        }
    }

    /**
     * 幂等确保 Consumer 存在（不存在则创建，已存在则更新）。
     *
     * <p>直接调用 jnats {@code addOrUpdateConsumer}：服务端会按 Consumer name 做 upsert，
     * 已存在则覆盖配置；本方法不区分 create/update 调用方语义。</p>
     *
     * @param streamName Stream 名称
     * @param def        Consumer 定义
     * @throws NatsException 当 jnats 抛任意异常（含 stream 不存在、配置非法等）时
     */
    public void ensureConsumerExists(String streamName, NatsProperties.ConsumerDefinition def) {
        try {
            var mgmt = connectionManager.getConnection().jetStreamManagement();
            var config = buildConsumerConfiguration(def);
            mgmt.addOrUpdateConsumer(streamName, config);
            log.info("JetStream consumer [{}] on stream [{}] ensured", def.getName(), streamName);
        } catch (Exception e) {
            throw new NatsException(
                    "Failed to ensure consumer exists: " + streamName + "/" + def.getName(), e);
        }
    }

    /**
     * 声明所有配置的 Stream 和 Consumer。
     *
     * <p>仅在 {@code enabled} 且 {@code auto-provision} 同时为 true 时执行；
     * 任一 stream 或 consumer 失败都会立即中止并抛异常，调用方需自行决定是否重试。</p>
     *
     * @param jetStreamConfig JetStream 配置
     * @throws NatsException 任意 {@link #ensureStreamExists} / {@link #ensureConsumerExists} 抛出时透传
     */
    public void provisionAll(NatsProperties.JetStream jetStreamConfig) {
        if (!jetStreamConfig.isEnabled() || !jetStreamConfig.isAutoProvision()) {
            return;
        }
        for (var stream : jetStreamConfig.getStreams()) {
            ensureStreamExists(stream);
            for (var consumer : stream.getConsumers()) {
                ensureConsumerExists(stream.getName(), consumer);
            }
        }
    }

    /**
     * 声明所有配置的 Stream、Consumer 以及 DLQ Stream。
     *
     * <p>在业务 stream/consumer 声明完成后,若 DLQ 功能启用,遍历业务 stream 列表,
     * 为每个业务 stream 自动 derive DLQ stream 并幂等声明。原有
     * {@link #provisionAll(NatsProperties.JetStream)} 行为不变,仅供无 DLQ 场景使用。</p>
     *
     * @param properties NATS 全量配置
     * @throws NatsException 业务 stream/consumer 声明或 DLQ stream 声明任一失败时抛出
     */
    public void provisionAll(NatsProperties properties) {
        provisionAll(properties.getJetstream());
        provisionDlqStreams(properties);
    }

    /**
     * 为每个业务 stream 派生并声明对应的 DLQ Stream
     *
     * <p>遍历业务 stream 列表,跳过名字以 DLQ 后缀结尾的 stream(防 advisory 自反),
     * 为剩余业务 stream 派生 DLQ stream 配置(name = 原名 + {@code streamNameSuffix},
     * subjects = 原 subjects + {@code subjectSuffix}),通过
     * {@code jsm.addStream} 幂等声明。</p>
     *
     * <p>DLQ stream 已存在但配置不同时跳过不覆盖(M1 fall-back)。
     * nats 2.10 不会在首次 js.subscribe("$JS.EVENT.ADVISORY.*") 时自动建承载 advisory
     * 事件的内部 stream,会导致 NatsDeadLetterAdvisoryConsumer 报 "No matching streams
     * for subject" 降级到 Core NATS fallback → DLQ 永远不工作。故此处先幂等预创建
     * {@code JSAPI_ADVISORY} stream,subjects = {@code $JS.EVENT.ADVISORY.>},
     * retention = Interest(自动清理已被 ack 的 advisory),storage = Memory(advisory 高频)。</p>
     *
     * @param properties NATS 全量配置
     */
    private void provisionDlqStreams(NatsProperties properties) {
        var dlq = properties.getJetstream().getDlq();
        if (!dlq.isEnabled()) {
            return;
        }
        var streamNameSuffix = dlq.getStreamNameSuffix();
        var subjectSuffix = dlq.getSubjectSuffix();
        try {
            var mgmt = connectionManager.getConnection().jetStreamManagement();
            // 预创建承载 advisory 事件的内部 stream,避免首次 js.subscribe 触发 No matching
            // streams for subject 错误导致 NatsDeadLetterAdvisoryConsumer 降级到 Core NATS
            // fallback → DLQ 永远不工作
            ensureAdvisoryStream(mgmt, dlq.getAdvisoryStreamName());
            for (var businessStream : properties.getJetstream().getStreams()) {
                if (businessStream.getName().endsWith(streamNameSuffix)) {
                    continue;
                }
                var dlqStreamName = businessStream.getName() + streamNameSuffix;
                var dlqSubjects = businessStream.getSubjects().stream()
                        .map(subject -> subject + subjectSuffix)
                        .toList();
                var config = StreamConfiguration.builder()
                        .name(dlqStreamName)
                        .subjects(dlqSubjects)
                        .storageType(parseStorageType(businessStream.getStorageType()))
                        .retentionPolicy(RetentionPolicy.Limits)
                        .discardPolicy(DiscardPolicy.Old)
                        .build();
                try {
                    mgmt.addStream(config);
                    log.info("DLQ stream [{}] provisioned", dlqStreamName);
                } catch (JetStreamApiException e) {
                    if (e.getApiErrorCode() == 10058) {
                        log.info("DLQ stream [{}] already exists, skipping", dlqStreamName);
                    } else {
                        throw new NatsException(
                                "Failed to provision DLQ stream: " + dlqStreamName, e);
                    }
                } catch (IOException e) {
                    throw new NatsException(
                            "Failed to provision DLQ stream: " + dlqStreamName, e);
                }
            }
        } catch (Exception e) {
            throw new NatsException("Failed to provision DLQ streams", e);
        }
    }

    /**
     * 幂等预创建承载 {@code $JS.EVENT.ADVISORY.>} 事件的内部 stream。
     *
     * <p>不存在则按 advisory 语义创建(Memory + Interest + Discard=New);
     * 已存在则跳过,绝不覆盖已有配置 — 同 DLQ stream 策略。</p>
     *
     * <p>错误码 {@code 404}(Not Found)与 {@code 10059}(Stream Not Found)均表示 stream
     * 未创建,均需走 addStream 分支;其他错误码抛出。</p>
     */
    private void ensureAdvisoryStream(JetStreamManagement mgmt, String advisoryStream)
            throws IOException, JetStreamApiException {
        try {
            mgmt.getStreamInfo(advisoryStream);
            log.info("Advisory stream [{}] already exists, skipping", advisoryStream);
        } catch (JetStreamApiException e) {
            int code = e.getErrorCode();
            if (code == 10059 || code == 404) {
                mgmt.addStream(StreamConfiguration.builder()
                        .name(advisoryStream)
                        .subjects("$JS.EVENT.ADVISORY.>")
                        .storageType(StorageType.Memory)
                        .retentionPolicy(RetentionPolicy.Interest)
                        .discardPolicy(DiscardPolicy.New)
                        .build());
                log.info("Advisory stream [{}] created", advisoryStream);
            } else {
                throw e;
            }
        }
    }

    // ===== 内部构建方法 =====

    /**
     * 将 {@link NatsProperties.StreamDefinition} 翻译为 jnats {@link StreamConfiguration}。
     *
     * <p>关键设计：所有 {@code > 0} / {@code != null} 的字段才设置，否则保持 jnats 默认；
     * 这是因为 jnats 的 builder 在未设置时使用服务端默认值，{@code 0} 显式赋值反而会被当作
     * "禁用容量限制" 的语义，与 YAML 默认未配置意图不符。</p>
     */
    private StreamConfiguration buildStreamConfiguration(NatsProperties.StreamDefinition def) {
        var builder = StreamConfiguration.builder()
                .name(def.getName())
                .subjects(def.getSubjects())
                .storageType(parseStorageType(def.getStorageType()))
                .retentionPolicy(parseRetentionPolicy(def.getRetention()))
                .replicas(def.getNumReplicas())
                .discardPolicy(parseDiscardPolicy(def.getDiscard()))
                .allowRollup(def.isAllowRollup())
                .denyDelete(def.isDenyDelete());

        if (def.getMaxAge() != null) {
            builder.maxAge(def.getMaxAge());
        }
        if (def.getMaxBytes() > 0) {
            builder.maxBytes(def.getMaxBytes());
        }
        if (def.getMaxMessages() > 0) {
            builder.maxMessages(def.getMaxMessages());
        }
        if (def.getMaxMessageSize() > 0) {
            builder.maximumMessageSize((int) def.getMaxMessageSize());
        }

        return builder.build();
    }

    /**
     * 将 {@link NatsProperties.ConsumerDefinition} 翻译为 jnats {@link ConsumerConfiguration}。
     *
     * <p>与 {@link #buildStreamConfiguration(NatsProperties.StreamDefinition)} 一致：
     * 仅在 YAML 显式配置时才把字段透传给 jnats，避免用零值覆盖服务端默认行为。
     * {@code sampleFrequency} 字段 jnats 接收字符串类型，需显式 {@code String.valueOf}。</p>
     */
    private ConsumerConfiguration buildConsumerConfiguration(NatsProperties.ConsumerDefinition def) {
        var builder = ConsumerConfiguration.builder()
                .name(def.getName())
                .ackPolicy(parseAckPolicy(def.getAckPolicy()))
                .deliverPolicy(parseDeliverPolicy(def.getDeliverPolicy()))
                .replayPolicy(parseReplayPolicy(def.getReplayPolicy()));

        if (def.getFilterSubject() != null && !def.getFilterSubject().isBlank()) {
            builder.filterSubject(def.getFilterSubject());
        }
        if (def.getAckWait() != null) {
            builder.ackWait(def.getAckWait());
        }
        if (def.getMaxDeliver() > 0) {
            builder.maxDeliver(def.getMaxDeliver());
        }
        if (def.getMaxAckPending() > 0) {
            builder.maxAckPending(def.getMaxAckPending());
        }
        if (def.getMaxWaiting() > 0) {
            builder.maxPullWaiting(def.getMaxWaiting());
        }
        if (def.getInactiveThreshold() != null) {
            builder.inactiveThreshold(def.getInactiveThreshold());
        }
        if (def.getRateLimit() > 0) {
            builder.rateLimit(def.getRateLimit());
        }
        if (def.getSampleFrequency() > 0) {
            builder.sampleFrequency(String.valueOf(def.getSampleFrequency()));
        }
        if (def.getBackoff() != null && !def.getBackoff().isEmpty()) {
            builder.backoff(def.getBackoff().toArray(Duration[]::new));
        }

        return builder.build();
    }

    /**
     * 将 YAML 中的小写 storage 类型字符串映射为 jnats {@link StorageType}。
     *
     * <p>未识别值兜底为 {@link StorageType#File}（服务端默认），避免因 YAML 误填导致建流失败。</p>
     */
    private StorageType parseStorageType(String type) {
        return switch (type.toLowerCase()) {
            case "memory" -> StorageType.Memory;
            default -> StorageType.File;
        };
    }

    /**
     * 映射 retention 策略：{@code interest} / {@code work-queue}（允许连字符与紧凑两种写法），
     * 其它一律按 {@link RetentionPolicy#Limits} 处理。
     */
    private RetentionPolicy parseRetentionPolicy(String policy) {
        return switch (policy.toLowerCase()) {
            case "interest" -> RetentionPolicy.Interest;
            case "work-queue", "workqueue" -> RetentionPolicy.WorkQueue;
            default -> RetentionPolicy.Limits;
        };
    }

    /**
     * 映射 discard 策略：仅显式 {@code new} 才使用 {@link DiscardPolicy#New}，
     * 默认 {@link DiscardPolicy#Old} 与 NATS 服务端默认一致。
     */
    private DiscardPolicy parseDiscardPolicy(String policy) {
        return switch (policy.toLowerCase()) {
            case "new" -> DiscardPolicy.New;
            default -> DiscardPolicy.Old;
        };
    }

    /**
     * 映射 ack 策略：{@code none}/{@code all} 显式映射，其余（含 {@code explicit}）
     * 一律按 jnats 最常用的 {@link AckPolicy#Explicit} 处理。
     */
    private AckPolicy parseAckPolicy(String policy) {
        return switch (policy.toLowerCase()) {
            case "none" -> AckPolicy.None;
            case "all" -> AckPolicy.All;
            default -> AckPolicy.Explicit;
        };
    }

    /**
     * 映射 deliver 策略：覆盖 NATS 支持的 5 种命名空间，全部映射为 jnats 枚举；
     * 未识别值兜底为 {@link DeliverPolicy#All}。
     */
    private DeliverPolicy parseDeliverPolicy(String policy) {
        return switch (policy.toLowerCase()) {
            case "last" -> DeliverPolicy.Last;
            case "new" -> DeliverPolicy.New;
            case "by-start-sequence" -> DeliverPolicy.ByStartSequence;
            case "by-start-time" -> DeliverPolicy.ByStartTime;
            case "last-per-subject" -> DeliverPolicy.LastPerSubject;
            default -> DeliverPolicy.All;
        };
    }

    /**
     * 映射 replay 策略：仅 {@code original} 显式映射，其余一律走 {@link ReplayPolicy#Instant}（服务端默认）。
     */
    private ReplayPolicy parseReplayPolicy(String policy) {
        return switch (policy.toLowerCase()) {
            case "original" -> ReplayPolicy.Original;
            default -> ReplayPolicy.Instant;
        };
    }
}
