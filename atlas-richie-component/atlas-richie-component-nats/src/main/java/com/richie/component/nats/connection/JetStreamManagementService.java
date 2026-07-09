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
package com.richie.component.nats.connection;

import com.richie.component.nats.config.NatsProperties;
import com.richie.component.nats.connection.NatsConnectionManager;
import com.richie.component.nats.exception.NatsException;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.DiscardPolicy;
import io.nats.client.api.ReplayPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

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
     * 幂等确保 Stream 存在（不存在则创建，已存在则跳过）
     *
     * @param def Stream 定义
     */
    public void ensureStreamExists(NatsProperties.StreamDefinition def) {
        try {
            var mgmt = connectionManager.getConnection().jetStreamManagement();
            try {
                mgmt.getStreamInfo(def.getName());
                log.info("JetStream stream [{}] already exists, skipping creation", def.getName());
            } catch (io.nats.client.JetStreamApiException e) {
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
     * 幂等确保 Consumer 存在（不存在则创建，已存在则更新）
     *
     * @param streamName Stream 名称
     * @param def        Consumer 定义
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
     * 声明所有配置的 Stream 和 Consumer
     *
     * @param jetStreamConfig JetStream 配置
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
     * 声明所有配置的 Stream、Consumer 以及 DLQ Stream
     *
     * <p>在业务 stream/consumer 声明完成后,若 DLQ 功能启用,遍历业务 stream 列表,
     * 为每个业务 stream 自动 derive DLQ stream 并幂等声明。原有
     * {@link #provisionAll(NatsProperties.JetStream)} 行为不变,仅供无 DLQ 场景使用。</p>
     *
     * @param properties NATS 全量配置
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
        var dlq = properties.getDlq();
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
            ensureAdvisoryStream(mgmt);
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
                } catch (io.nats.client.JetStreamApiException e) {
                    if (e.getApiErrorCode() == 10058) {
                        log.info("DLQ stream [{}] already exists, skipping", dlqStreamName);
                    } else {
                        throw new NatsException(
                                "Failed to provision DLQ stream: " + dlqStreamName, e);
                    }
                } catch (java.io.IOException e) {
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
    private void ensureAdvisoryStream(io.nats.client.JetStreamManagement mgmt)
            throws java.io.IOException, io.nats.client.JetStreamApiException {
        final String advisoryStream = "JSAPI_ADVISORY";
        try {
            mgmt.getStreamInfo(advisoryStream);
            log.info("Advisory stream [{}] already exists, skipping", advisoryStream);
        } catch (io.nats.client.JetStreamApiException e) {
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

        return builder.build();
    }

    private StorageType parseStorageType(String type) {
        return switch (type.toLowerCase()) {
            case "memory" -> StorageType.Memory;
            default -> StorageType.File;
        };
    }

    private RetentionPolicy parseRetentionPolicy(String policy) {
        return switch (policy.toLowerCase()) {
            case "interest" -> RetentionPolicy.Interest;
            case "work-queue", "workqueue" -> RetentionPolicy.WorkQueue;
            default -> RetentionPolicy.Limits;
        };
    }

    private DiscardPolicy parseDiscardPolicy(String policy) {
        return switch (policy.toLowerCase()) {
            case "new" -> DiscardPolicy.New;
            default -> DiscardPolicy.Old;
        };
    }

    private AckPolicy parseAckPolicy(String policy) {
        return switch (policy.toLowerCase()) {
            case "none" -> AckPolicy.None;
            case "all" -> AckPolicy.All;
            default -> AckPolicy.Explicit;
        };
    }

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

    private ReplayPolicy parseReplayPolicy(String policy) {
        return switch (policy.toLowerCase()) {
            case "original" -> ReplayPolicy.Original;
            default -> ReplayPolicy.Instant;
        };
    }
}
