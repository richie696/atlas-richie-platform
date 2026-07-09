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
package com.richie.component.statemachine.storage;

import com.richie.component.statemachine.config.StateMachineProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 状态机 Redis Key 构建器
 * <p>
 * 统一管理所有状态机相关的 Redis key 生成，确保 key 格式一致性和可维护性。
 *
 * <p>
 * <strong>Key 类型：</strong>
 *
 * <ul>
 *   <li><b>当前状态</b>：{keyPrefix}:state:{stateMachineName}:{businessId}</li>
 *   <li><b>状态历史</b>：{keyPrefix}:history:{stateMachineName}:{businessId}:{timestamp}</li>
 *   <li><b>历史列表</b>：{keyPrefix}:history:list:{stateMachineName}:{businessId}</li>
 *   <li><b>同步键</b>：{stateMachineName}:{businessId}（用于消息传递，不包含前缀）</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class StateMachineKeyBuilder {

    /**
     * 状态机配置属性
     */
    private final StateMachineProperties properties;

    /**
     * 获取统一的前缀
     *
     * @return Redis key 前缀
     */
    public String getKeyPrefix() {
        String prefix = properties.getRedisStream().getKeyPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return "platform:statemachine";
        }
        // 确保前缀不以冒号结尾
        return prefix.endsWith(":") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    /**
     * 构建当前状态缓存键
     * <p>格式：{keyPrefix}:state:{stateMachineName}:{businessId}
     * <p>示例：platform:statemachine:state:order:123456
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务对象ID（Long 类型）
     * @return Redis key
     */
    public String buildCurrentStateKey(String stateMachineName, Long businessId) {
        return "%s:state:%s:%s".formatted(getKeyPrefix(), stateMachineName, String.valueOf(businessId));
    }

    /**
     * 构建状态历史缓存键（包含时间戳，用于唯一标识）
     * <p>格式：{keyPrefix}:history:{stateMachineName}:{businessId}:{timestamp}
     * <p>示例：platform:statemachine:history:order:123456:1234567890
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务对象ID（Long 类型）
     * @param createTime 创建时间（用于生成时间戳）
     * @return Redis key
     */
    public String buildHistoryKey(String stateMachineName, Long businessId, LocalDateTime createTime) {
        String timestamp = createTime != null
            ? String.valueOf(createTime.toEpochSecond(java.time.ZoneOffset.UTC))
            : String.valueOf(System.currentTimeMillis());
        return "%s:history:%s:%s:%s".formatted(getKeyPrefix(), stateMachineName, String.valueOf(businessId), timestamp);
    }

    /**
     * 构建状态历史列表缓存键
     * <p>格式：{keyPrefix}:history:list:{stateMachineName}:{businessId}
     * <p>示例：platform:statemachine:history:list:order:123456
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务对象ID（Long 类型）
     * @return Redis key
     */
    public String buildHistoryListKey(String stateMachineName, Long businessId) {
        return "%s:history:list:%s:%s".formatted(getKeyPrefix(), stateMachineName, String.valueOf(businessId));
    }

    /**
     * 构建同步键（用于消息传递，不包含前缀）
     * <p>
     * 同步键用于标识需要同步的状态，格式：{stateMachineName}:{businessId}
     * 不包含 Redis key 前缀，因为这只是用于消息传递的标识符。
     *
     * <p>格式：{stateMachineName}:{businessId}
     * <p>示例：order:123456
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务对象ID（Long 类型）
     * @return 同步键字符串
     */
    public String buildSyncKey(String stateMachineName, Long businessId) {
        return "%s:%s".formatted(stateMachineName, String.valueOf(businessId));
    }

    /**
     * 构建数据库同步队列键
     * <p>格式：{keyPrefix}:db:sync:queue
     * <p>示例：platform:statemachine:db:sync:queue
     *
     * @return Redis key
     */
    public String buildDbSyncQueueKey() {
        return "%s:db:sync:queue".formatted(getKeyPrefix());
    }

    /**
     * 构建数据库同步去重 Set 键
     * <p>格式：{keyPrefix}:db:sync:set
     * <p>示例：platform:statemachine:db:sync:set
     *
     * @return Redis key
     */
    public String buildDbSyncSetKey() {
        return "%s:db:sync:set".formatted(getKeyPrefix());
    }

    /**
     * 构建数据库同步锁键
     * <p>格式：{keyPrefix}:db:sync:lock
     * <p>示例：platform:statemachine:db:sync:lock
     *
     * @return Redis key
     */
    public String buildDbSyncLockKey() {
        return "%s:db:sync:lock".formatted(getKeyPrefix());
    }

    /**
     * 构建 Redis Stream 同步键（用于数据库持久化）
     * <p>格式：{keyPrefix}:db:sync
     * <p>示例：platform:statemachine:db:sync
     *
     * @return Stream key
     */
    public String buildDbSyncStreamKey() {
        return "%s:db:sync".formatted(getKeyPrefix());
    }

    /**
     * 构建状态迁转序列号键
     * <p>格式：{keyPrefix}:seq:{stateMachineName}:{businessId}
     */
    public String buildSeqKey(String stateMachineName, Long businessId) {
        return "%s:seq:%s:%s".formatted(getKeyPrefix(), stateMachineName, String.valueOf(businessId));
    }

}

