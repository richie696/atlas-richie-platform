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
package com.richie.component.mqtt.filter.handler.impl;

import com.richie.context.common.api.SpringContextHolder;
import com.richie.context.utils.security.HashUtils;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.filter.datasource.DatasourceHandler;
import com.richie.component.mqtt.filter.handler.MessageHandler;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * HiveMQ 消息处理器实现类
 * <p>
 * 专门用于 HiveMQ MQTT 5.0 客户端的消息处理，处理 {@code Mqtt5Publish} 类型的消息。
 * <p>
 * <strong>消息格式说明：</strong>
 * <ul>
 *   <li>HiveMQ 实现中，{@code Mqtt5Publish} 的 payload 直接是业务数据（不再是 ConsumerMessage 的序列化结果）</li>
 *   <li>直接从 {@code Mqtt5Publish} 的 payload 计算 hash 值，调用 {@code DatasourceHandler} 进行去重</li>
 * </ul>
 * <p>
 * <strong>处理流程：</strong>
 * <ol>
 *   <li>从 {@code Mqtt5Publish} 中提取 payload</li>
 *   <li>使用 SHA-256 计算 payload 的 hash 值</li>
 *   <li>调用 {@code DatasourceHandler} 进行去重和缓存</li>
 * </ol>
 *
 * @author richie696
 * @version 2.0
 * @since 2025-01-04
 */
@Slf4j
@Service("hiveMqMessageHandler")
@RequiredArgsConstructor
public class HiveMqMessageHandler implements MessageHandler<Mqtt5Publish> {

    private final MqttClientProperties properties;

    /**
     * 检查是否是重复消息的方法
     * <p>
     * 直接从 {@code Mqtt5Publish} 的 payload 计算 hash 值，调用 {@code DatasourceHandler} 进行去重检查。
     *
     * @param publish 待检查的 MQTT 发布消息
     * @return 返回检查结果（true：是重复消息，false：不是重复消息）
     */
    @Override
    public boolean isDuplicate(Mqtt5Publish publish) {
        if (publish == null) {
            return true;
        }

        try {
            byte[] payload = publish.getPayloadAsBytes();
            if (payload.length == 0) {
                return true;
            }
            String hash = HashUtils.sha256(payload);
            return getDatasourceHandler().isDuplicate(hash);
        } catch (Exception e) {
            log.error("计算 Mqtt5Publish payload hash 失败: {}", e.getMessage(), e);
            // 计算失败时，为了安全起见，认为消息重复，避免重复处理
            return true;
        }
    }

    /**
     * 保存消息数据的方法
     * <p>
     * 直接从 {@code Mqtt5Publish} 的 payload 计算 hash 值，调用 {@code DatasourceHandler} 保存缓存。
     *
     * @param publish 待保存的 MQTT 发布消息
     * @param expired 该消息的过期时间（单位：毫秒）
     */
    @Override
    public void saveCache(Mqtt5Publish publish, long expired) {
        if (publish == null) {
            return;
        }

        try {
            byte[] payload = publish.getPayloadAsBytes();
            if (payload == null || payload.length == 0) {
                return;
            }
            String hash = HashUtils.sha256(payload);
            getDatasourceHandler().saveCache(hash, expired);
        } catch (Exception e) {
            log.error("计算 Mqtt5Publish payload hash 失败: {}", e.getMessage(), e);
            // 计算失败时，记录错误但不抛出异常，避免影响消息处理流程
        }
    }

    /**
     * 获取数据源处理器
     *
     * @return DatasourceHandler 实例
     */
    private DatasourceHandler getDatasourceHandler() {
        return SpringContextHolder.getBean(properties.getDatasource().getHandlerName());
    }
}
