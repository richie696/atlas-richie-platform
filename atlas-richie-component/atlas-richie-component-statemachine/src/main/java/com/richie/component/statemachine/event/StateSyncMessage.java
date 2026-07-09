/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.statemachine.event;

import com.richie.contract.model.BaseStreamMessage;

/**
 * 状态同步消息
 * <p>
 * 用于 Redis Stream 中传输状态同步请求，遵循核心原则：
 * <ol>
 *   <li><b>只包含同步键</b>：仅包含 stateMachineName 和 businessId，不包含状态数据</li>
 *   <li><b>从 Redis 读取状态</b>：消费者从 Redis（通过 StateStorage）读取最新状态</li>
 *   <li><b>避免数据过时</b>：不携带状态数据，确保读取的是最新状态</li>
 *   <li><b>减少消息大小</b>：消息体小，传输效率高</li>
 * </ol>
 *
 * <p>
 * <strong>同步键格式：</strong>{stateMachineName}:{businessId}（不包含 Redis key 前缀）
 *
 *
 * @param syncKey 同步键，格式：stateMachineName:businessId
 * @author richie696
 * @since 1.0.0
 */
public record StateSyncMessage(String syncKey) implements BaseStreamMessage {

    /**
     * 构建状态同步消息
     * <p>
     * 使用 {@link StateSyncKey#build(String, Long)} 统一构建同步键。
     *
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务对象ID（Long 类型）
     * @return 状态同步消息
     */
    public static StateSyncMessage of(String stateMachineName, Long businessId) {
        String syncKey = StateSyncKey.build(stateMachineName, businessId);
        return new StateSyncMessage(syncKey);
    }

    /**
     * 解析同步键，获取状态机名称和业务ID
     *
     * @return 解析结果，包含 stateMachineName 和 businessId
     */
    public StateSyncKey parse() {
        return StateSyncKey.parse(syncKey);
    }

    /**
     * 获取状态机名称
     *
     * @return 状态机名称
     */
    public String getStateMachineName() {
        StateSyncKey key = parse();
        return key != null ? key.stateMachineName() : null;
    }

    /**
     * 获取业务对象ID
     *
     * @return 业务对象ID（Long 类型）
     */
    public Long getBusinessId() {
        StateSyncKey key = parse();
        return key != null ? key.businessId() : null;
    }
}

